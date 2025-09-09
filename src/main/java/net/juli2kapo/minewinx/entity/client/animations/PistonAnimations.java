package net.juli2kapo.minewinx.entity.client.animations;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public class PistonAnimations {
    public static final AnimationDefinition APLASTAR = AnimationDefinition.Builder.withLength(0.8751F)
        .addAnimation("Top", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 16.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("Extender2", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("Extender3", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 9.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("Extender4", new AnimationChannel(AnimationChannel.Targets.POSITION, 
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 16.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();
}