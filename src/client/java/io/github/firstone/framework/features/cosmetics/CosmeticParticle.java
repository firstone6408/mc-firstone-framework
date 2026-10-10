package io.github.firstone.framework.features.cosmetics;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.List;

/**
 * Particle that plays the player's frames ({@link CosmeticsFiles}) from the first to the last over its life
 *
 * <p>It moves like the particle it replaces, so the effect keeps the game's motion:</p>
 * <ul>
 *   <li>{@link #death} — like {@code ExplodeParticle} ({@code poof}): rises slowly and slows down</li>
 *   <li>{@link #itemBreak} — like {@code BreakingItemParticle}: thrown forward and falls</li>
 * </ul>
 *
 * <p>Differences on purpose: the whole frame is drawn (the item crack shows a quarter of the item texture), it is
 * not tinted gray (the poof is), and it is drawn translucent so the frames may use soft transparency.</p>
 */
public final class CosmeticParticle extends TextureSheetParticle {

    /** Frames in play order (never empty) */
    private final List<TextureAtlasSprite> frames;

    /** Particle at rest; the factory sets its motion */
    private CosmeticParticle(ClientLevel level, double x, double y, double z, List<TextureAtlasSprite> frames) {
        super(level, x, y, z);
        this.frames = frames;
        setSprite(frames.get(0));
    }

    /** Particle with the game's random spread added to the given motion */
    private CosmeticParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                             List<TextureAtlasSprite> frames) {
        super(level, x, y, z, xd, yd, zd);
        this.frames = frames;
        setSprite(frames.get(0));
    }

    /**
     * Creates a death particle, moving like the game's {@code poof} ({@code ExplodeParticle})
     *
     * @param level    the client level
     * @param x        start x
     * @param y        start y
     * @param z        start z
     * @param xd       motion x (the game uses a small random value)
     * @param yd       motion y
     * @param zd       motion z
     * @param frames   frames to play, not empty
     * @param size     size multiplier, 1 = the game's size
     * @param duration life multiplier, 1 = the game's duration
     * @param speed    motion multiplier, 1 = the game's speed
     * @return the particle, to add with {@code ParticleEngine.add}
     */
    public static CosmeticParticle death(ClientLevel level, double x, double y, double z, double xd, double yd,
                                         double zd, List<TextureAtlasSprite> frames, float size, float duration,
                                         float speed) {
        CosmeticParticle particle = new CosmeticParticle(level, x, y, z, frames);
        particle.gravity = -0.1F;
        particle.friction = 0.9F;
        particle.xd = (xd + (Math.random() * 2.0 - 1.0) * 0.05F) * speed;
        particle.yd = (yd + (Math.random() * 2.0 - 1.0) * 0.05F) * speed;
        particle.zd = (zd + (Math.random() * 2.0 - 1.0) * 0.05F) * speed;
        particle.quadSize = 0.1F * (particle.random.nextFloat() * particle.random.nextFloat() * 6.0F + 1.0F) * size;
        particle.setLifetime((int) (16.0 / (particle.random.nextFloat() * 0.8 + 0.2)) + 2, duration);
        return particle;
    }

    /**
     * Creates an item break particle, moving like the game's item crack ({@code BreakingItemParticle})
     *
     * @param level    the client level
     * @param x        start x
     * @param y        start y
     * @param z        start z
     * @param xd       motion x (the game throws it forward from the eyes)
     * @param yd       motion y
     * @param zd       motion z
     * @param frames   frames to play, not empty
     * @param size     size multiplier, 1 = the game's size
     * @param duration life multiplier, 1 = the game's duration
     * @return the particle, to add with {@code ParticleEngine.add}
     */
    public static CosmeticParticle itemBreak(ClientLevel level, double x, double y, double z, double xd, double yd,
                                             double zd, List<TextureAtlasSprite> frames, float size, float duration) {
        CosmeticParticle particle = new CosmeticParticle(level, x, y, z, 0.0, 0.0, 0.0, frames);
        particle.xd = particle.xd * 0.1F + xd;
        particle.yd = particle.yd * 0.1F + yd;
        particle.zd = particle.zd * 0.1F + zd;
        particle.gravity = 1.0F;
        particle.quadSize = particle.quadSize / 2.0F * size;
        particle.setLifetime(particle.lifetime, duration);
        return particle;
    }

    /** Sets the life in ticks: the game's life times the duration multiplier, at least 1 tick */
    private void setLifetime(int gameLifetime, float duration) {
        this.lifetime = Math.max(1, Math.round(gameLifetime * duration));
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive()) {
            // same frame choice as the game's SpriteSet.get(age, lifetime)
            int frame = this.age * (this.frames.size() - 1) / this.lifetime;
            setSprite(this.frames.get(Math.min(frame, this.frames.size() - 1)));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
