package dev.andante.dodgebolt.game;

import dev.andante.dodgebolt.Dodgebolt;
import dev.andante.dodgebolt.ItemEntityAccess;
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
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static dev.andante.dodgebolt.util.Constants.ALPHA_ARROW_SPAWN_POS;
import static dev.andante.dodgebolt.util.Constants.ALPHA_POSITIONS;
import static dev.andante.dodgebolt.util.Constants.ARENA_MAX;
import static dev.andante.dodgebolt.util.Constants.ARENA_MID_Z;
import static dev.andante.dodgebolt.util.Constants.ARENA_MIN;
import static dev.andante.dodgebolt.util.Constants.ARENA_POS;
import static dev.andante.dodgebolt.util.Constants.ARENA_SPAWN_POS;
import static dev.andante.dodgebolt.util.Constants.BETA_ARROW_SPAWN_POS;
import static dev.andante.dodgebolt.util.Constants.BETA_POSITIONS;
import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

public class DodgeboltGame {
    private final GameTeam teamAlpha;
    private final GameTeam teamBeta;

    private int round;
    private int scoreAlpha, scoreBeta;

    private RoundStage stage;
    private int tick;
    private EdgeManager edgeManager;
    private final List<ServerPlayerEntity> eliminated;

    public DodgeboltGame(GameTeam alpha, GameTeam beta) {
        this.teamAlpha = alpha;
        this.teamBeta = beta;
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
        this.edgeManager = new EdgeManager();
        this.eliminated.clear();

        this.requestRespawn(server);

        for (ServerPlayerEntity player : this.getAlive(server)) {
            player.setHealth(player.getMaxHealth());
            this.setupInventory(player);
        }

        ServerWorld world = server.getOverworld();
        world.getEntitiesByType(TypeFilter.instanceOf(Entity.class), entity -> entity instanceof ItemEntity || entity instanceof ArrowEntity).forEach(Entity::discard);
        StructureHelper.placeArena(world, ARENA_POS, this.teamAlpha, this.teamBeta);
        this.setupBarriers(world, false);
        this.teleportTeamsToSpawn(server, world);

        if (this.round == 1) {
            List<ServerPlayerEntity> alphaPlayers = this.teamAlpha.getPlayers(server);
            List<ServerPlayerEntity> betaPlayers = this.teamBeta.getPlayers(server);
            List<ServerPlayerEntity> players = PlayerLookup.all(server).stream().filter(player -> !alphaPlayers.contains(player) && !betaPlayers.contains(player)).toList();
            for (ServerPlayerEntity player : players) {
                player.teleport(world, ARENA_SPAWN_POS.getX(), ARENA_SPAWN_POS.getY(), ARENA_SPAWN_POS.getZ(), 0.0F, 0.0F);
            }
        }
    }

    public void setupInventory(ServerPlayerEntity player, boolean clear) {
        PlayerInventory inventory = player.getInventory();
        if (clear) {
            inventory.clear();
        }
        ItemStack stack = new ItemStack(Items.BOW);
        stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        player.giveItemStack(stack);
    }

    public void setupInventory(ServerPlayerEntity player) {
        this.setupInventory(player, true);
    }

    public void terminate(MinecraftServer server) {
        this.requestRespawn(server);

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
            // player.sendMessage(Text.literal(this.stage.name() + " " + this.round + " " + tick + " " + second), true);
            player.sendMessage(
                    Text.empty()
                        .append(Text.literal("" + this.scoreAlpha).setStyle(Dodgebolt.getTeamStyle(this.teamAlpha)))
                        .append(" | ")
                        .append(Text.literal("" + this.scoreBeta).setStyle(Dodgebolt.getTeamStyle(this.teamBeta))), true
            );
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
                                                      Text.literal("▶" + countdown + "◀").formatted(Formatting.BOLD, countdown == 3
                                                              ? Formatting.RED : countdown == 2
                                                              ? Formatting.YELLOW : countdown == 1
                                                              ? Formatting.GREEN : Formatting.WHITE
                                                      )
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
                    if (alphaPlayers.size() > betaPlayers.size()) {
                        this.scoreAlpha++;
                    } else {
                        this.scoreBeta++;
                    }

                    this.endRound(server);
                } else {
                    for (ServerPlayerEntity player : this.getAlive(server)) {
                        boolean isAlpha = this.teamAlpha.getPlayers(server).contains(player);
                        double z = player.getZ();
                        if (isAlpha ? z >= ARENA_MID_Z : z <= ARENA_MID_Z) {
                            TitleHelper.sendTimes(player, 0, 5, 0);
                            TitleHelper.sendTitle(player, Text.empty(), Text.literal("<< RETURN TO YOUR HALF >>").formatted(Formatting.BOLD, Formatting.RED));
                            player.damage(DamageSource.IN_WALL, 2.0F);
                            player.getInventory().remove(stack -> stack.isOf(Items.BOW), -1, player.playerScreenHandler.getCraftingInput());
                        } else {
                            if (!player.getInventory().contains(new ItemStack(Items.BOW))) {
                                this.setupInventory(player, false);
                            }
                        }

                        if (player.isInLava()) {
                            player.kill();
                        }
                    }
                }

                if (tick % TICKS_PER_SECOND == 0) {
                    if (second != 0 && second % 20 == 0) {
                        this.edgeManager.queue();
                    }
                }

                this.edgeManager.tick(server);
            }

            case POST -> {
                int max = 5;
                if (second >= max) {
                    this.triggerRound(server);
                }

                this.edgeManager.tick(server);
            }

            case END -> {
                if (second >= 10) {
                    Dodgebolt.DODGEBOLT_MANAGER.tryEnd(server);
                }
            }
        }

        this.tick++;
    }

    public void onHitBlock(ArrowEntity entity, BlockHitResult hit) {
        if (!entity.getScoreboardTags().contains("item_immune")) {
            ItemEntity itemEntity = new ItemEntity(entity.world, entity.getX(), entity.getY(), entity.getZ(), new ItemStack(Items.ARROW));
            itemEntity.setVelocity(0.0D, 0.15D, 0.0D);
            itemEntity.setPickupDelay(5);
            entity.world.spawnEntity(itemEntity);

            entity.discard();
        }
    }

    public void onHitEntity(ArrowEntity entity, EntityHitResult hit) {
        if (hit.getEntity() instanceof ServerPlayerEntity player) {
            if (entity.getOwner() instanceof PlayerEntity owner) {
                if (owner.getScoreboardTeam() != player.getScoreboardTeam()) {
                    player.damage(DamageSource.arrow(entity, owner), Float.MAX_VALUE);
                    owner.addExperience(1);
                    entity.dropItem(Items.ARROW);
                    entity.discard();
                }
            }
        }
    }

    public void onJoin(ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        if (this.teamAlpha.getPlayers(server).contains(player) || this.teamBeta.getPlayers(server).contains(player)) {
            this.eliminated.add(player);
        }

        this.requestRespawn(player);
    }

    public void onArrowItemDestroyed(ItemEntity entity) {
        for (int i = 0, l = entity.getStack().getCount(); i < l; i++) {
            this.spawnArrow(entity.world, entity.squaredDistanceTo(ALPHA_ARROW_SPAWN_POS) < entity.squaredDistanceTo(BETA_ARROW_SPAWN_POS) ? ALPHA_ARROW_SPAWN_POS : BETA_ARROW_SPAWN_POS);
        }
    }

    public void onItemTick(ItemEntity entity) {
        if (this.stage == RoundStage.IN_GAME) {
            ItemEntityAccess access = (ItemEntityAccess) entity;
            if (!entity.world.getBlockState(entity.getBlockPos().down()).isOf(Blocks.ICE)) {
                access.setTimer(access.getTimer() + 1);

                if (access.getTimer() > 30) {
                    entity.damage(DamageSource.IN_WALL, Float.MAX_VALUE);
                }
            } else {
                access.setTimer(0);
            }
        }

        ItemStack stack = entity.getStack();
        if (stack.isOf(Items.BOW)) {
            entity.discard();
        } else if (stack.isOf(Items.ARROW)) {
            entity.setGlowing(true);
        }
    }

    public void spawnArrow(World world, Vec3d pos) {
        ArrowEntity entity = EntityType.ARROW.create(world);
        if (entity != null) {
            entity.updatePositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            entity.setVelocity(new Vec3d(0.0D, 0.3D, 0.0D));
            entity.pickupType = PickupPermission.ALLOWED;
            entity.addScoreboardTag("item_immune");
            entity.setYaw(0.0F);
            entity.setPitch(0.0F);
            world.spawnEntity(entity);
        }
    }

    public void requestRespawn(ServerPlayerEntity player) {
        player.networkHandler.onClientStatus(new ClientStatusC2SPacket(Mode.PERFORM_RESPAWN));
    }

    public void requestRespawn(MinecraftServer server) {
        new ArrayList<>(PlayerLookup.all(server)).forEach(this::requestRespawn);
    }

    public void onDeath(ServerPlayerEntity player, DamageSource source, float amount) {
        this.eliminated.add(player);
        this.onEliminated(player, source.getAttacker());

        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.velocityDirty = true;
    }

    protected void onEliminated(ServerPlayerEntity player, @Nullable Entity attacker) {
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

            ServerPlayerEntity attackerPlayer = Optional.ofNullable(attacker)
                                                        .filter(ServerPlayerEntity.class::isInstance)
                                                        .map(ServerPlayerEntity.class::cast)
                                                        .orElse(null);

            for (ServerPlayerEntity xplayer : PlayerLookup.all(server)) {
                MutableText text = Text.empty().formatted(Formatting.GRAY).append(Text.literal("[☠] ").formatted(Formatting.RED)).append(Dodgebolt.getDisplayName(player));
                if (attackerPlayer != null) {
                    text.append(" was shot by ").append(Dodgebolt.getDisplayName(attackerPlayer));
                } else {
                    text.append(" died");
                }

                xplayer.sendMessage(text);

                this.playSound(xplayer, "early_elimination");
            }
        }

        PlayerInventory inventory = player.getInventory();
        player.dropStack(new ItemStack(Items.ARROW, inventory.remove(stack -> stack.isOf(Items.ARROW), 0, player.playerScreenHandler.getCraftingInput())));
        inventory.clear();

        this.edgeManager.queue();
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

    public List<ServerPlayerEntity> getAlive(MinecraftServer server) {
        List<ServerPlayerEntity> list = new ArrayList<>(this.getAliveOf(server, this.teamAlpha));
        list.addAll(this.getAliveOf(server, this.teamBeta));
        return list;
    }

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

        if (this.scoreAlpha >= 3 || this.scoreBeta >= 3) {
            this.stage = RoundStage.END;
            GameTeam winner = this.scoreAlpha > this.scoreBeta ? this.teamAlpha : this.teamBeta;
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                TitleHelper.sendTimes(player, 0, 40, 0);
                TitleHelper.sendTitle(player, Text.literal("GAME OVER").formatted(Formatting.BOLD, Formatting.RED), Text.literal(winner.name() + " WIN!").setStyle(Dodgebolt.getTeamStyle(winner)));

                this.stopMusic(player);
                this.playSoundFast(player, "game_end");
                this.playSoundFast(player, "advance");
            }
        } else {
            this.stage = RoundStage.POST;
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                TitleHelper.sendTimes(player, 0, 40, 0);
                TitleHelper.sendTitle(player, Text.literal("ROUND OVER").formatted(Formatting.BOLD, Formatting.RED), Text.empty());

                this.stopMusic(player);
                this.playSound(player, "game_end");
                this.playSound(player, "team_eliminated");
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

    public class EdgeManager {
        public static final int DURATION = 3 * 20;
        public static final int FLASH_INTERVAL = DURATION / 10;

        private final Map<BlockPos, BlockState> flipMap;

        private int tick, lastDesired;
        private int desired, stage;

        public EdgeManager() {
            this.flipMap = new HashMap<>();
        }

        public void tick(MinecraftServer server) {
            if (this.stage != this.lastDesired) {
                if (this.tick > DURATION) {
                    ServerWorld world = server.getOverworld();
                    for (BlockPos pos : calculatePerimeter(this.stage, this.lastDesired)) {
                        world.setBlockState(pos.withY(11), Blocks.AIR.getDefaultState());
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }

                    this.stage = this.lastDesired;
                    this.lastDesired = this.desired;
                } else {
                    if (this.tick % FLASH_INTERVAL == 0) {
                        ServerWorld world = server.getOverworld();
                        for (BlockPos pos : calculatePerimeter(this.stage, this.lastDesired)) {
                            BlockState state = this.getStateFromMap(pos);
                            BlockPos carpetPos = pos.withY(11);
                            if (state == null) {
                                this.flipMap.put(pos, world.getBlockState(carpetPos));
                                world.setBlockState(pos, Blocks.LAPIS_ORE.getDefaultState());
                                world.setBlockState(carpetPos, Blocks.AIR.getDefaultState());
                            } else {
                                world.setBlockState(carpetPos, state);
                                world.setBlockState(pos, Blocks.ICE.getDefaultState());
                                this.removeFlipMapPos(pos);
                            }
                        }
                    }

                    this.tick++;
                }
            } else {
                if (this.lastDesired != this.desired) {
                    this.lastDesired = this.desired;
                    this.tick = 0;

                    for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                        DodgeboltGame.this.playSound(player, "platform_decay");
                    }
                }
            }
        }

        public BlockState getStateFromMap(BlockPos pos) {
            return this.flipMap.entrySet().stream().filter(createPredicate(pos)).map(Map.Entry::getValue).findAny().orElse(null);
        }

        public void removeFlipMapPos(BlockPos pos) {
            this.flipMap.entrySet().removeIf(createPredicate(pos));
        }

        public Predicate<Map.Entry<BlockPos, BlockState>> createPredicate(BlockPos pos) {
            return entry -> {
                BlockPos xpos = entry.getKey();
                return xpos.getX() == pos.getX() && xpos.getZ() == pos.getZ();
            };
        }

        public void add(int rows) {
            this.desired = Math.min(this.desired + rows, 9);
        }

        public void queue() {
            this.add(this.desired == 0 ? 2 : 1);
        }

        public List<BlockPos> calculatePerimeter(int minx, int minz, int maxx, int maxz) {
            List<BlockPos> list = new ArrayList<>();
            for (int i = minx; i <= maxx; i++) {
                this.add(list, i, minz);
                this.add(list, i, maxz);
            }
            for (int i = minz; i <= maxz; i++) {
                this.add(list, minx, i);
                this.add(list, maxx, i);
            }

            return list;
        }

        public List<BlockPos> calculatePerimeter(int minx, int minz, int maxx, int maxz, int indent, int width) {
            List<BlockPos> list = new ArrayList<>();
            for (int i = indent; i < width; i++) {
                List<BlockPos> ilist = calculatePerimeter(minx + i, minz + i, maxx - i, maxz - i);
                for (BlockPos pos : ilist) {
                    this.add(list, pos.getX(), pos.getZ());
                }
            }
            return list;
        }

        public List<BlockPos> calculatePerimeter(int indent, int width) {
            return calculatePerimeter(ARENA_MIN.getX(), ARENA_MIN.getZ(), ARENA_MAX.getX(), ARENA_MAX.getZ(), indent, width);
        }

        public void add(List<BlockPos> list, int x, int z) {
            if (list.stream().noneMatch(posx -> posx.getX() == x && posx.getZ() == z)) {
                list.add(new BlockPos(x, 10, z));
            }
        }
    }
}
