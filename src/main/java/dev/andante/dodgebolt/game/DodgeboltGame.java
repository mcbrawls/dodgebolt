package dev.andante.dodgebolt.game;

import dev.andante.dodgebolt.Dodgebolt;
import dev.andante.dodgebolt.util.StructureHelper;
import dev.andante.dodgebolt.util.TitleHelper;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static dev.andante.dodgebolt.util.Constants.ALPHA_ARROW_SPAWN_POS;
import static dev.andante.dodgebolt.util.Constants.ALPHA_POSITIONS;
import static dev.andante.dodgebolt.util.Constants.ARENA_MID_Z;
import static dev.andante.dodgebolt.util.Constants.ARENA_POS;
import static dev.andante.dodgebolt.util.Constants.ARENA_SPAWN_POS;
import static dev.andante.dodgebolt.util.Constants.BETA_ARROW_SPAWN_POS;
import static dev.andante.dodgebolt.util.Constants.BETA_POSITIONS;
import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

public class DodgeboltGame {
    private final GameTeam teamAlpha = GameTeam.RED;
    private final GameTeam teamBeta = GameTeam.BLUE;

    private final int max;

    private int round;

    private RoundStage stage;
    private int tick;
    private final List<ServerPlayerEntity> eliminated;

    public DodgeboltGame(int max) {
        this.max = max;
        this.eliminated = new ArrayList<>();
    }

    public void initialize(MinecraftServer server) {
        this.triggerRound(server);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    public void triggerRound(MinecraftServer server) {
        this.round++;
        this.stage = RoundStage.PRE;
        this.tick = 0;
        this.eliminated.clear();

        for (ServerPlayerEntity player : new ArrayList<>(PlayerLookup.all(server))) {
            this.requestRespawn(player);
            this.setupInventory(player);
        }

        ServerWorld world = server.getOverworld();
        world.getEntitiesByType(TypeFilter.instanceOf(Entity.class), entity -> entity instanceof ItemEntity || entity instanceof ArrowEntity).forEach(Entity::discard);
        StructureHelper.placeArena(world, ARENA_POS, this.teamAlpha, this.teamBeta);
        this.setupBarriers(world, false);
        this.teleportTeamsToSpawn(server, world);
    }

    public void setupInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack stack = new ItemStack(Items.BOW);
        stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        inventory.setStack(0, stack);
    }

    public void terminate(MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            this.stopMusic(player);
            player.getInventory().clear();
        }

        ServerWorld world = server.getOverworld();
        this.setupBarriers(world, true);

        float spawnAngle = world.getSpawnAngle();
        Vec3d spawnPos = Vec3d.ofBottomCenter(world.getSpawnPos());
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            player.teleport(world, spawnPos.x, spawnPos.y, spawnPos.z, spawnAngle, 0.0F);
        }
    }

    public void tick(MinecraftServer server) {
        int second = tick / TICKS_PER_SECOND;

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            player.sendMessage(Text.literal(this.stage.name() + " " + this.round + " " + tick + " " + second), true);
        }

        switch (this.stage) {
            case PRE -> {
                int max = 15;
                if (second >= max) {
                    this.startRound(server);
                } else {
                    if (this.tick % TICKS_PER_SECOND == 0) {
                        int countdown = max - second;
                        if (countdown <= 10) {
                            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                                TitleHelper.sendTimes(player, 0, 30, 0);
                                TitleHelper.sendTitle(player, Text.literal("Starting in").formatted(Formatting.AQUA),
                                                      Text.literal("")
                                                          .append(Text.literal("▶"))
                                                          .append(Text.literal("" + countdown).formatted(Formatting.BOLD))
                                                          .append(Text.literal("◀"))
                                );

                                if (countdown <= 3) {
                                    this.playSound(player, "start_claxon");
                                }
                            }
                        }
                    }
                }
            }

            case IN_GAME -> {
                List<ServerPlayerEntity> alphaPlayers = this.getAliveOf(server, this.teamAlpha);
                List<ServerPlayerEntity> betaPlayers = this.getAliveOf(server, this.teamBeta);

                if (alphaPlayers.isEmpty() || betaPlayers.isEmpty()) {
                    this.endRound(server);
                } else {
                    for (ServerPlayerEntity player : alphaPlayers) {
                        if (player.getZ() >= ARENA_MID_Z) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 2, 9, false, false, true));
                        }
                    }

                    for (ServerPlayerEntity player : betaPlayers) {
                        if (player.getZ() <= ARENA_MID_Z) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 2, 9, false, false, true));
                        }
                    }
                }
            }

            case POST -> {
                int max = 5;
                if (second >= max) {
                    this.triggerRound(server);
                }
            }

            case END -> {
                if (second >= 10) {
                    Dodgebolt.DODGEBOLT_MANAGER.tryEnd(server);
                }
            }
        }

        this.tick++;
    }

    public void onJoin(ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        if (this.teamAlpha.getPlayers(server).contains(player) || this.teamBeta.getPlayers(server).contains(player)) {
            this.eliminated.add(player);
        }

        this.requestRespawn(player);
    }

    public void onArrowItemDestroyed(ItemEntity entity) {
        this.spawnArrow(entity.world, entity.squaredDistanceTo(ALPHA_ARROW_SPAWN_POS) < entity.squaredDistanceTo(BETA_ARROW_SPAWN_POS) ? ALPHA_ARROW_SPAWN_POS : BETA_ARROW_SPAWN_POS);
    }

    public void spawnArrow(World world, Vec3d pos) {
        ArrowEntity entity = EntityType.ARROW.create(world);
        if (entity != null) {
            entity.updatePositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            entity.setVelocity(new Vec3d(0.0D, 0.3D, 0.0D));
            entity.pickupType = PickupPermission.ALLOWED;
            entity.addScoreboardTag("item_immune");
            world.spawnEntity(entity);
        }
    }

    public void requestRespawn(ServerPlayerEntity player) {
        player.networkHandler.onClientStatus(new ClientStatusC2SPacket(Mode.PERFORM_RESPAWN));
    }

    public void onDeath(ServerPlayerEntity player, DamageSource source, float amount) {
        this.eliminated.add(player);
        this.onEliminated(player);
    }

    protected void onEliminated(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            int alphaPlayers = this.getAliveOf(server, this.teamAlpha).size();
            int betaPlayers = this.getAliveOf(server, this.teamBeta).size();
            if ((alphaPlayers == 1 && betaPlayers > 1) || (betaPlayers == 1 && alphaPlayers > 1)) {
                for (ServerPlayerEntity xplayer : PlayerLookup.all(server)) {
                    this.stopMusic(xplayer);
                    this.playSoundFast(xplayer, "dodgebolt_loop");
                }
            }
        }
    }

    public void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity player, boolean alive) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            player.teleport(server.getOverworld(), ARENA_SPAWN_POS.getX(), ARENA_SPAWN_POS.getY(), ARENA_SPAWN_POS.getZ(), 0.0F, 0.0F);
        }

        this.setupInventory(player);
    }

    public List<ServerPlayerEntity> getAliveOf(MinecraftServer server, GameTeam team) {
        return team.getPlayers(server).stream().filter(Predicate.not(this.eliminated::contains)).toList();
    }

    /*public List<ServerPlayerEntity> getAlive(MinecraftServer server) {
        List<ServerPlayerEntity> list = new ArrayList<>(this.getAliveOf(server, this.teamAlpha));
        list.addAll(this.getAliveOf(server, this.teamBeta));
        return list;
    }*/

    /**
     * Called on every round start.
     */
    private void startRound(MinecraftServer server) {
        this.stage = RoundStage.IN_GAME;
        this.tick = 0;

        ServerWorld world = server.getOverworld();
        this.setupBarriers(world, true);
        this.spawnArrow(world, ALPHA_ARROW_SPAWN_POS);
        this.spawnArrow(world, BETA_ARROW_SPAWN_POS);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));

            if (this.round == 1) {
                this.playSound(player, "dodgebolt_resume");
            }

            this.playSound(player, "start_claxon_final");
        }
    }

    /**
     * Called on every round end.
     */
    private void endRound(MinecraftServer server) {
        this.tick = 0;

        if (this.round == this.max) {
            this.stage = RoundStage.END;
        } else {
            this.stage = RoundStage.POST;

            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                TitleHelper.sendTimes(player, 0, 40, 0);
                TitleHelper.sendTitle(player, Text.literal("ROUND OVER").formatted(Formatting.BOLD, Formatting.RED), Text.empty());

                this.stopMusic(player);
                this.playSound(player, "game_end");
                this.playSound(player, "dodgebolt");
            }
        }
    }

    private void setupBarriers(World world, boolean remove) {
        BlockState state = remove ? Blocks.AIR.getDefaultState() : Blocks.BARRIER.getDefaultState();
        for (List<BlockPos> positions : List.of(ALPHA_POSITIONS, BETA_POSITIONS)) {
            for (BlockPos pos : positions) {
                for (int i = 0; i < 2; i++) {
                    world.setBlockState(pos.add(1, i, 0), state);
                    world.setBlockState(pos.add(0, i, 1), state);
                    world.setBlockState(pos.add(-1, i, 0), state);
                    world.setBlockState(pos.add(0, i, -1), state);
                }
            }
        }
    }

    public void teleportTeamsToSpawn(MinecraftServer server, ServerWorld world) {
        List<ServerPlayerEntity> alphaPlayers = this.teamAlpha.getPlayers(server);
        for (ServerPlayerEntity player : alphaPlayers) {
            List<BlockPos> positions = ALPHA_POSITIONS;
            int index = alphaPlayers.indexOf(player);
            int i = index % positions.size();
            Vec3d pos = Vec3d.ofBottomCenter(positions.get(i));
            player.teleport(world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        }

        List<ServerPlayerEntity> betaPlayers = this.teamBeta.getPlayers(server);
        for (ServerPlayerEntity player : betaPlayers) {
            List<BlockPos> positions = BETA_POSITIONS;
            int index = betaPlayers.indexOf(player);
            int i = index % positions.size();
            Vec3d pos = Vec3d.ofBottomCenter(positions.get(i));
            player.teleport(world, pos.x, pos.y, pos.z, 180.0F, 0.0F);
        }
    }

    private void playSound(ServerPlayerEntity player, String id) {
        player.networkHandler.sendPacket(new PlaySoundIdS2CPacket(new Identifier(Dodgebolt.MOD_ID, id), SoundCategory.VOICE, Vec3d.ZERO, 1.0F, 1.0F, 0L));
    }

    private void playSoundFast(ServerPlayerEntity player, String id) {
        player.networkHandler.sendPacket(new PlaySoundIdS2CPacket(new Identifier(Dodgebolt.MOD_ID, id), SoundCategory.VOICE, Vec3d.ZERO, 1.0F, 1.2F, 0L));
    }

    private void stopMusic(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.VOICE));
    }

    public enum RoundStage {
        PRE,
        IN_GAME,
        POST,
        END
    }
}
