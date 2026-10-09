package gidor.deepdarkgen;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.SculkShrieker;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class DeepDarkChunkGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(org.bukkit.World world) {
        return List.of(new SculkVeinPopulator());
    }

    private static class SculkVeinPopulator extends BlockPopulator {

        private final MultipleFacing floorSculkVein;

        public SculkVeinPopulator() {
            this.floorSculkVein = (MultipleFacing) Bukkit.createBlockData(Material.SCULK_VEIN);
            this.floorSculkVein.setFace(org.bukkit.block.BlockFace.DOWN, true);
        }

        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            int startX = chunkX << 4;
            int startZ = chunkZ << 4;
            long seed = worldInfo.getSeed();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = startX + x;
                    int worldZ = startZ + z;

                    if (!region.isInRegion(worldX, 64, worldZ)) {
                        continue;
                    }

                    int topY = region.getHighestBlockYAt(worldX, worldZ);

                    // Surface Population
                    if (topY > worldInfo.getMinHeight()) {
                        processLocation(worldInfo, random, region, seed, worldX, topY, worldZ);
                    }

                    // Underground Cave & Mineshaft Population (Dynamic Y max handles mountain caves)
                    int minY = worldInfo.getMinHeight() + 4;
                    int maxY = Math.max(minY, topY - 1);

                    for (int y = minY; y < maxY; y++) {
                        if (!region.isInRegion(worldX, y, worldZ)) continue;

                        Material current = region.getType(worldX, y, worldZ);
                        Material below = region.getType(worldX, y - 1, worldZ);

                        if ((current == Material.AIR || current == Material.CAVE_AIR) && below.isSolid()) {
                            processLocation(worldInfo, random, region, seed, worldX, y, worldZ);
                        }
                    }
                }
            }
        }

        private void processLocation(WorldInfo worldInfo, Random random, LimitedRegion region, 
                                     long seed, int worldX, int y, int worldZ) {

            if (!region.isInRegion(worldX, y, worldZ)) {
                return;
            }

            Material targetBlock = region.getType(worldX, y, worldZ);
            Material blockBelow = region.getType(worldX, y - 1, worldZ);

            // Ignore liquids, ice, and already placed sculk feature blocks below
            if (blockBelow == Material.WATER || blockBelow == Material.LAVA || blockBelow == Material.ICE 
                    || blockBelow == Material.SCULK_VEIN || blockBelow == Material.SCULK_SENSOR 
                    || blockBelow == Material.SCULK_SHRIEKER) {
                return;
            }

            if (targetBlock == Material.SCULK_VEIN || targetBlock == Material.SCULK_SENSOR 
                    || targetBlock == Material.SCULK_SHRIEKER) {
                return;
            }

            // Thread-safe Simplex noise calculation based on global world seed & coordinates
            double noiseValue = evaluateNoise(worldX, worldZ, seed);
            if (noiseValue > 0.20) {
                int groundY = y;
                while (groundY > worldInfo.getMinHeight() && region.isInRegion(worldX, groundY, worldZ) 
                        && !region.getType(worldX, groundY, worldZ).isSolid()) {
                    groundY--;
                }
                if (region.isInRegion(worldX, groundY, worldZ)) {
                    Material solidType = region.getType(worldX, groundY, worldZ);
                    if (solidType == Material.GRASS_BLOCK || solidType == Material.DIRT 
                            || solidType == Material.STONE || solidType == Material.DEEPSLATE) {
                        region.setType(worldX, groundY, worldZ, Material.SCULK);
                    }
                }
            }

            // Sculk veins randomly placed as foliage (65% chance)
            if (random.nextFloat() < 0.65f) {
                if ((targetBlock == Material.AIR || targetBlock == Material.CAVE_AIR || targetBlock == Material.SHORT_GRASS)
                        && blockBelow.isSolid()) {
                    region.setBlockData(worldX, y, worldZ, floorSculkVein);
                }
            }

            // Sculk sensors and active shriekers randomly placed (2.0% chance)
            if (random.nextFloat() < 0.020f) {
                if (random.nextFloat() < 0.77f) {
                    region.setType(worldX, y, worldZ, Material.SCULK_SENSOR);
                } else {
                    region.setType(worldX, y, worldZ, Material.SCULK_SHRIEKER);
                    SculkShrieker shrieker = (SculkShrieker) Bukkit.createBlockData(Material.SCULK_SHRIEKER);
                    shrieker.setCanSummon(true);
                    region.setBlockData(worldX, y, worldZ, shrieker);
                }
            }
        }

        // Thread-safe, non-stateful 2D Simplex noise generator
        private double evaluateNoise(int x, int z, long seed) {
            double scale = 0.05;
            double nx = x * scale;
            double nz = z * scale;
            double s = (seed & 0xFFFF) * 0.001;
            
            double val = Math.sin(nx + s) * Math.cos(nz + s)
                       + 0.5 * Math.sin(nx * 2.1 + nz * 1.5)
                       + 0.25 * Math.cos(nx * 4.3 - nz * 3.1);
            return val / 1.75;
        }
    }
}