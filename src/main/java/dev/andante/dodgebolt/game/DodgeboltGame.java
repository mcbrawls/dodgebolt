package dev.andante.dodgebolt.game;

import dev.andante.dodgebolt.Dodgebolt;
import dev.andante.dodgebolt.processor.ArenaStructureProcessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static dev.andante.dodgebolt.Constants.ALPHA_ARROW_SPAWN_POS;
import static dev.andante.dodgebolt.Constants.ALPHA_POSITIONS;
import static dev.andante.dodgebolt.Constants.ARENA_MID_Z;
import static dev.andante.dodgebolt.Constants.ARENA_POS;
import static dev.andante.dodgebolt.Constants.ARENA_SPAWN_POS;
import static dev.andante.dodgebolt.Constants.ARENA_STRUCTURE_ID;
import static dev.andante.dodgebolt.Constants.BETA_ARROW_SPAWN_POS;
import static dev.andante.dodgebolt.Constants.BETA_POSITIONS;
import static dev.andante.dodgebolt.Constants.SPAWN_POS;
import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

public final class DodgeboltGame {
    public static final DodgeboltGame INSTANCE = new DodgeboltGame();

    private State state;
    private int tick, round;
    private Data data;
    private final List<String> eliminated;
    private final EdgeManager edgeManager;

    public DodgeboltGame() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayConnectionEvents.JOIN.register(this::onJoin);
        ServerPlayerEvents.ALLOW_DEATH.register(this::onDeath);

        this.state = State.LOBBY;
        this.tick = -1;
        this.round = 0;
        this.data = null;
        this.eliminated = new ArrayList<>();
        this.edgeManager = new EdgeManager();
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            HungerManager hungerManager = player.getHungerManager();
            hungerManager.setFoodLevel(20);
            player.setOnFire(false);
            player.setFireTicks(0);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, Integer.MAX_VALUE, 0, false, false));
        }

        this.edgeManager.tick(server);

        if (this.state == State.LOBBY) {
            this.tick = -1;
            this.round = 0;
            this.data = null;
            this.eliminated.clear();
            this.edgeManager.reset(null, false);
        } else {
            this.tick++;

            if (this.state == State.START) {
                if (this.tick % TICKS_PER_SECOND == 0) {
                    int seconds = (this.tick / TICKS_PER_SECOND) - 4;
                    if (seconds >= 10) {
                        this.state = State.PLAY;
                        this.tick = 0;

                        ServerWorld world = server.getOverworld();
                        this.setupBarriers(world, true);
                        this.spawnArrow(world, Vec3d.ofBottomCenter(ALPHA_ARROW_SPAWN_POS));
                        this.spawnArrow(world, Vec3d.ofBottomCenter(BETA_ARROW_SPAWN_POS));

                        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
                            this.playSound(player, "start_claxon_final");
                        }
                    } else if (seconds >= 0) {
                        /*if (seconds == 0) {
                            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                                this.playSound(player, "game_countdown");
                            }
                        }*/

                        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                            ServerPlayNetworkHandler handler = player.networkHandler;
                            handler.sendPacket(new TitleFadeS2CPacket(0, 30, 0));
                            handler.sendPacket(new SubtitleS2CPacket(Text.literal("▶%s◀".formatted(10 - seconds)).formatted(Formatting.BOLD)));
                            handler.sendPacket(new TitleS2CPacket(Text.literal("Starting in").formatted(Formatting.AQUA, Formatting.BOLD)));
                            this.playSound(player, seconds >= 10 - 3 ? "start_claxon" : "timer_pop");
                        }
                    }
                }
            } else if (this.state == State.PLAY) {
                if (this.tick != 0 && this.tick % (10 * 20) == 0) {
                    this.edgeManager.reduce();
                }

                List<ServerPlayerEntity> active = PlayerLookup.all(server).stream().filter(player -> !this.eliminated.contains(player.getEntityName())).toList();

                List<ServerPlayerEntity> activeAlpha = active.stream().filter(player -> {
                    AbstractTeam team = player.getScoreboardTeam();
                    if (team != null) {
                        GameTeam gameTeam = GameTeam.of(team);
                        return gameTeam == this.data.alpha();
                    }

                    return false;
                }).toList();

                List<ServerPlayerEntity> actveBeta = active.stream().filter(player -> {
                    AbstractTeam team = player.getScoreboardTeam();
                    if (team != null) {
                        GameTeam gameTeam = GameTeam.of(team);
                        return gameTeam == this.data.beta();
                    }

                    return false;
                }).toList();

                if (activeAlpha.isEmpty() || actveBeta.isEmpty()) {
                    this.state = State.END;
                    this.tick = 0;

                    server.getOverworld().getEntitiesByType(TypeFilter.instanceOf(Entity.class), entity -> entity instanceof ArrowEntity || (entity instanceof ItemEntity itemEntity && itemEntity.getStack().isOf(Items.ARROW))).forEach(Entity::discard);

                    for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                        ServerPlayNetworkHandler handler = player.networkHandler;
                        handler.sendPacket(new TitleFadeS2CPacket(0, 40, 0));
                        handler.sendPacket(new SubtitleS2CPacket(Text.literal("%s WIN".formatted(activeAlpha.size() > actveBeta.size() ? this.data.alpha().name() : this.data.beta().name()))));
                        handler.sendPacket(new TitleS2CPacket(Text.literal("ROUND OVER").formatted(Formatting.RED, Formatting.BOLD)));

                        handler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.VOICE));
                        this.playSound(player, "Ldodgebolt_loop");
                        this.playSound(player, "game_end");

                        if (this.edgeManager.flip) {
                            this.edgeManager.reset(server.getOverworld(), true);
                        }

                        player.getInventory().clear();
                    }
                } else {
                    for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                        AbstractTeam team = player.getScoreboardTeam();
                        if (team != null) {
                            GameTeam gameTeam = GameTeam.of(team);
                            boolean isAlpha = gameTeam == this.data.alpha();
                            if (isAlpha || gameTeam == this.data.beta()) {
                                if (active.contains(player)) {
                                    double z = player.getZ();
                                    if (isAlpha ? (z >= ARENA_MID_Z) : (z <= ARENA_MID_Z)) {
                                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 2, 9, false, false, true));

                                        ServerPlayNetworkHandler handler = player.networkHandler;
                                        handler.sendPacket(new TitleFadeS2CPacket(0, 5, 0));
                                        handler.sendPacket(new SubtitleS2CPacket(Text.literal("YOU ARE NOT IN YOUR PLAY AREA").formatted(Formatting.RED)));
                                        handler.sendPacket(new TitleS2CPacket(Text.empty()));

                                        player.getInventory().remove(ItemStack::hasNbt, 1, player.playerScreenHandler.getCraftingInput());
                                    } else {
                                        this.updateBow(player);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (this.state == State.END) {
                if (this.tick >= (10 * 20)) {
                    if (this.round >= 3) {
                        this.end(server);
                    } else {
                        this.state = State.START;
                        this.tick = 0;
                        this.round++;
                        this.eliminated.clear();
                        this.edgeManager.reset(null, false);
                        this.teleportTeamsToSpawn(server);
                        this.setupBarriers(server.getOverworld(), false);
                        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                            player.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.VOICE));
                            this.playSound(player, "dodgebolt");
                        }
                    }
                }
            }
        }
    }

    public void playSound(ServerPlayerEntity player, String id) {
        float pitch = 1.0F;

        if (id.equals("Pdodgebolt_loop")) {
            id = "dodgebolt_loop";
            pitch = 1.05F;
        } else if (id.equals("Ldodgebolt_loop")) {
            id = "dodgebolt_loop";
            pitch = 0.95F;
        }

        player.networkHandler.sendPacket(new PlaySoundIdS2CPacket(new Identifier(Dodgebolt.MOD_ID, id), SoundCategory.VOICE, Vec3d.ZERO, 1.0F, pitch, 0L));
    }

    public boolean onDeath(ServerPlayerEntity player, DamageSource source, float amount) {
        this.eliminate(player);
        player.setHealth(player.getMaxHealth());
        return false;
    }

    public void eliminate(ServerPlayerEntity player) {
        this.eliminated.add(player.getEntityName());
        player.getInventory().clear();
        this.teleportToSpawn(player, player.getWorld());
        this.edgeManager.reduce();
        if (this.edgeManager.stage <= 4) {
            for (ServerPlayerEntity playerx : PlayerLookup.all(player.server)) {
                this.playSound(playerx, "platform_decay");
            }
        }
    }

    public void updateBow(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        if (!inventory.containsAny(Set.of(Items.BOW))) {
            ItemStack stack = new ItemStack(Items.BOW);
            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putBoolean("Unbreakable", true);
            inventory.setStack(0, stack);
        }
    }

    public void setupBarriers(ServerWorld world, boolean remove) {
        BlockState state = remove ? Blocks.AIR.getDefaultState() : Blocks.BARRIER.getDefaultState();
        for (List<BlockPos> positions : List.of(ALPHA_POSITIONS, BETA_POSITIONS)) {
            for (BlockPos pos : positions) {
                world.setBlockState(pos.add(1, 0, 0), state);
                world.setBlockState(pos.add(0, 0, 1), state);
                world.setBlockState(pos.add(-1, 0, 0), state);
                world.setBlockState(pos.add(0, 0, -1), state);
            }
        }
    }

    public void tryStart(ServerWorld world, GameTeam alpha, GameTeam beta) {
        if (this.state != State.LOBBY) {
            throw new IllegalStateException("Dodgebolt game active");
        }

        this.data = new Data(alpha, beta);

        for (ServerPlayerEntity player : PlayerLookup.all(world.getServer())) {
            player.getInventory().clear();
            this.updateBow(player);
        }

        this.state = State.START;
        this.tick = 0;
        this.round = 1;
        this.eliminated.clear();
        this.edgeManager.reset(null, false);
        MinecraftServer server = world.getServer();
        this.teleportTeamsToSpawn(server);
        this.setupBarriers(world, false);
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            player.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.VOICE));
            this.playSound(player, "dodgebolt");
        }
    }

    public void teleportTeamsToSpawn(MinecraftServer server) {
        ArenaStructureProcessor.placeStructure(server.getOverworld(), ARENA_POS, ARENA_STRUCTURE_ID, this.data.alpha(), this.data.beta());

        ServerWorld world = server.getOverworld();
        for (int z = 61; z <= 93; z++) {
            for (int x = -6; x <= 22; x++) {
                world.setBlockState(new BlockPos(x, -59, z), Blocks.AIR.getDefaultState());
                world.setBlockState(new BlockPos(x, -60, z), Blocks.AIR.getDefaultState());
            }
        }

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            AbstractTeam team = player.getScoreboardTeam();
            if (team != null) {
                GameTeam gameTeam = GameTeam.of(team);
                boolean isAlpha = gameTeam == this.data.alpha();
                if (isAlpha || gameTeam == this.data.beta()) {
                    List<String> players = new ArrayList<>(team.getPlayerList());
                    int index = players.indexOf(player.getEntityName());
                    if (index != -1) {
                        List<BlockPos> positions = isAlpha ? ALPHA_POSITIONS : BETA_POSITIONS;
                        int i = index % positions.size();
                        Vec3d pos = Vec3d.ofBottomCenter(positions.get(i));
                        player.teleport(player.getWorld(), pos.x, pos.y, pos.z, isAlpha ? 0.0F : 180.0F, 0.0F);
                    }
                }
            }
        }
    }

    public void end(MinecraftServer server) {
        this.state = State.LOBBY;
        this.data = null;
        this.tick = -1;
        this.round = 0;
        this.eliminated.clear();

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            this.teleportToSpawn(player, player.getWorld());
            player.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.VOICE));
        }
    }

    public void onJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        handler.player.kill();
    }

    public void onHitBlock(ArrowEntity entity, BlockHitResult hit) {
        Block block = entity.world.getBlockState(hit.getBlockPos()).getBlock();
        if (!(block instanceof CarpetBlock) && block != Blocks.ICE) {
            this.spawnArrow(entity.world, Vec3d.ofBottomCenter(
                entity.squaredDistanceTo(Vec3d.ofBottomCenter(ALPHA_ARROW_SPAWN_POS)) < entity.squaredDistanceTo(Vec3d.ofBottomCenter(BETA_ARROW_SPAWN_POS))
                    ? ALPHA_ARROW_SPAWN_POS : BETA_ARROW_SPAWN_POS
            ));
            entity.discard();
        }
    }

    private void spawnArrow(World world, Vec3d pos) {
        ArrowEntity nu = EntityType.ARROW.create(world);
        if (nu != null) {
            nu.updatePositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            nu.setVelocity(new Vec3d(0.0D, 0.3D, 0.0D));
            nu.pickupType = PickupPermission.ALLOWED;
            world.spawnEntity(nu);
        }
    }

    public void onHitEntity(ArrowEntity entity, EntityHitResult hit) {
        if (hit.getEntity() instanceof ServerPlayerEntity player) {
            if (entity.getOwner() instanceof PlayerEntity owner) {
                if (owner.getScoreboardTeam() != player.getScoreboardTeam()) {
                    player.kill();
                    owner.addExperience(1);
                }
            }
        }
    }

    public void teleportToSpawn(ServerPlayerEntity player, ServerWorld world) {
        Vec3d pos = Vec3d.ofBottomCenter(this.state == State.LOBBY ? SPAWN_POS : ARENA_SPAWN_POS);
        player.teleport(world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
    }

    public boolean isRunning() {
        return this.state != State.LOBBY;
    }

    public State getState() {
        return this.state;
    }

    public void onArrowTick(ArrowEntity arrow) {
        if (arrow.getWorld() instanceof ServerWorld world) {
            Entity owner = arrow.getOwner();
            if (owner != null) {
                int color = owner.getTeamColorValue();
                float r = ((color >> 16 ) & 0xFF) / 255.0f;
                float g = ((color >> 8  ) & 0xFF) / 255.0f;
                float b = ((color       ) & 0xFF) / 255.0f;
                world.spawnParticles(new DustParticleEffect(new Vec3f(r, g, b), 1), arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

    public enum State {
        LOBBY,
        START,
        PLAY,
        END
    }

    public record Data(GameTeam alpha, GameTeam beta) {
    }

    public static class EdgeManager {
        public static final List<BlockPos> STAGE_1 = create(new BlockPos(-6, 10, 61), 28, 32, 2);

        public static List<BlockPos> create(BlockPos origin, int width, int height, int indent) {
            List<BlockPos> list = new ArrayList<>();
            for (int i = 0; i < width; i++) {
                origin.add(i, 0, 0);
            }
            for (int i = 1; i < height; i++) {
                origin.add(0, 0, i);
                origin.add(width - 1, 0, i);
            }
            for (int i = 1; i < width - 1; i++) {
                origin.add(i, 0, height - 1);
            }
            return list;
        }

        private int stage;
        private int tick;
        private boolean reduce;
        private boolean flip;

        public EdgeManager() {
        }

        public void tick(MinecraftServer server) {
            if (this.reduce) {
                this.tick++;

                if (this.tick % (20 / 3) == 0) {
                    this.flip(server.getOverworld());
                }

                if (this.tick >= (10 * (20 / 3))) {
                    this.remove(server.getOverworld());
                }
            } else {
                this.reset(null, false);
            }
        }

        public void reduce() {
            this.reduce = true;
            this.stage++;
        }

        public void reset(ServerWorld world, boolean remove) {
            if (remove) {
                this.remove(world);
            }

            this.stage = 0;
            this.tick = 0;
            this.reduce = false;
            this.flip = false;
        }

        public void remove(ServerWorld world) {
            for (BlockPos pos : this.getStage()) {
                world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
        }

        public void flip(ServerWorld world) {
            this.flip = !flip;
            for (BlockPos pos : this.getStage()) {
                if (this.flip) {
                    world.setBlockState(pos.withY(-60), world.getBlockState(pos));
                    world.setBlockState(pos.withY(-59), world.getBlockState(pos.up()));

                    world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
                    world.setBlockState(pos, Blocks.LAPIS_ORE.getDefaultState());
                } else {
                    world.setBlockState(pos, world.getBlockState(pos.withY(-60)));
                    world.setBlockState(pos.up(), world.getBlockState(pos.withY(-59)));
                }
            }
        }

        public List<BlockPos> getStage() {
            return switch (this.stage) {
                case 1 -> create(new BlockPos(-6, 10, 61), 28, 32, 2);
                /*case 2 -> STAGE_2;
                case 3 -> STAGE_3;
                case 4 -> STAGE_4;*/
                default -> Collections.emptyList();
            };
        }
    }
}
