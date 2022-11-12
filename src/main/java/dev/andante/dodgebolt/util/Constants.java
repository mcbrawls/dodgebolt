package dev.andante.dodgebolt.util;

import dev.andante.dodgebolt.Dodgebolt;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public interface Constants {
    BlockPos SPAWN_POS = new BlockPos(8, 1, 8);
    BlockPos ARENA_SPAWN_POS = new BlockPos(8, 15, 57);

    Vec3d ALPHA_ARROW_SPAWN_POS = new Vec3d(8.5D, 12, 71.5D);
    Vec3d BETA_ARROW_SPAWN_POS = new Vec3d(8.5D, 12, 83.5D);

    int ARENA_MID_Z = 77;

    List<BlockPos> ALPHA_POSITIONS = List.of(
        new BlockPos(11, 12, 65),
        new BlockPos(5, 12, 65),
        new BlockPos(17, 12, 68),
        new BlockPos(-1, 12, 68)
    );

    List<BlockPos> BETA_POSITIONS = List.of(
        new BlockPos(5, 12, 89),
        new BlockPos(11, 12, 89),
        new BlockPos(-1, 12, 86),
        new BlockPos(17, 12, 86)
    );

    BlockPos ARENA_POS = new BlockPos(-7, 0, 60);
    Identifier ARENA_STRUCTURE_ID = new Identifier(Dodgebolt.MOD_ID, "arena");
}
