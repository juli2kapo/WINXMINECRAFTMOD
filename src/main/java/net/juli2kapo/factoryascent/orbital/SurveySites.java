package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Where every Ground Station and Launch Pad is, and who placed it: the survey map marks the
 * viewer's team's sites (the team is looked up when the map is drawn, so sites follow their
 * placer between teams). Blocks add themselves while loaded and remove themselves when broken.
 */
public final class SurveySites extends SavedData {
    public static final int STATION = 0, PAD = 1;

    public record Site(ResourceKey<Level> dimension, BlockPos pos, int kind, UUID owner) {
        static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Site::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Site::pos),
                Codec.INT.fieldOf("kind").forGetter(Site::kind),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Site::owner)
        ).apply(i, Site::new));
    }

    private static final Codec<SurveySites> CODEC = Site.CODEC.listOf().xmap(SurveySites::new, s -> s.sites);
    private static final SavedDataType<SurveySites> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "survey_sites"), () -> new SurveySites(List.of()), CODEC);

    private final List<Site> sites;

    private SurveySites(List<Site> sites) {
        this.sites = new ArrayList<>(sites);
    }

    public static SurveySites get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Adds or updates the site at that position. */
    public void put(ResourceKey<Level> dimension, BlockPos pos, int kind, UUID owner) {
        Site site = new Site(dimension, pos.immutable(), kind, owner);
        for (int i = 0; i < sites.size(); i++) {
            Site s = sites.get(i);
            if (s.dimension().equals(dimension) && s.pos().equals(pos)) {
                if (!s.equals(site)) {
                    sites.set(i, site);
                    setDirty();
                }
                return;
            }
        }
        sites.add(site);
        setDirty();
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (sites.removeIf(s -> s.dimension().equals(dimension) && s.pos().equals(pos))) setDirty();
    }

    /** The sites in a dimension whose placer is on that team now. */
    public List<Site> ofTeam(MinecraftServer server, String team, ResourceKey<Level> dimension) {
        FactoryTeams teams = FactoryTeams.get(server);
        return sites.stream().filter(s -> s.dimension().equals(dimension) && teams.teamOf(s.owner()).equals(team)).toList();
    }
}
