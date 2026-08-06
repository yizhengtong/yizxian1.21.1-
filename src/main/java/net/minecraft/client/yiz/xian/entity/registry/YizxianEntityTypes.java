package net.minecraft.client.yiz.xian.entity.registry;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 生物实体注册中心。
 */
public final class YizxianEntityTypes {

    private YizxianEntityTypes() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, YizxianMod.MODID);

    /**
     * 全首者 Boss。碰撞箱按身体（脚→肩/头），不含展开的翅膀（翅膀与头肩平齐、翼尖下垂插地）。
     * 体积放大 1.5 倍，与渲染 MODEL_SCALE 1.2 匹配：身体高约 3.75 格。
     */
    public static final Supplier<EntityType<QuanshouzheEntity>> QUANSHOUZHE =
        ENTITY_TYPES.register("quanshouzhe", () -> EntityType.Builder
            .of((EntityType<QuanshouzheEntity> type, net.minecraft.world.level.Level level) ->
                    new QuanshouzheEntity(type, level), MobCategory.MONSTER)
            .sized(1.8f, 3.9f)
            .fireImmune()
            .build(YizxianMod.MODID + ":quanshouzhe"));
}
