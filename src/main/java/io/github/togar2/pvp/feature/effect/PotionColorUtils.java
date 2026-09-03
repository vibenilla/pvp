package io.github.togar2.pvp.feature.effect;

import net.minestom.server.potion.Potion;

import java.util.Collection;

class PotionColorUtils {
    public static int getPotionColor(Collection<Potion> effects) {
        int red = 0, green = 0, blue = 0;
        var totalAmplifier = 0;

        for (var potion : effects) {
            if (potion.hasParticles()) {
                var color = potion.effect().color();
                var amplifier = potion.amplifier() + 1;
                red += amplifier * (color >> 16 & 0xFF);
                green += amplifier * (color >> 8 & 0xFF);
                blue += amplifier * (color & 0xFF);
                totalAmplifier += amplifier;
            }
        }

        if (totalAmplifier == 0) {
            return -1;
        } else {
            return rgba(255, red / totalAmplifier, green / totalAmplifier, blue / totalAmplifier);
        }
    }

    public static int rgba(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
