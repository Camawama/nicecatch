package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.server.FishBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holding your catch like a catch: a held fish item renders as the actual fish ENTITY in
 * the holder's hands — in first and third person both — instead of the flat item sprite. A
 * light fish dangles head-down from the one hand; a heavy one is laid across the arms. The
 * entity model is scaled from the caught item's stamped weight, so a trophy tuna in
 * someone's arms looks like a trophy.
 *
 * Wired in at {@code ItemInHandRenderer.renderItem} (see ItemInHandRendererMixin) — the one
 * spot both the first-person hands and every third-person {@code ItemInHandLayer} (players,
 * armor stands, cod-carrying zombies) funnel through, with the pose stack already sitting
 * at the hand socket. The fish item is mapped to its entity by the same registry-name
 * convention the catch system uses; items with no matching entity fall back to the vanilla
 * item render. Display entities are cached per type, never added to the world, and marked
 * so FishPitchRenderer leaves them alone.
 */
public final class FishCarryRenderer
{
    /** At and past this weight (lbs) the fish is laid across the arms instead of dangled. */
    private static final float TWO_HAND_LBS = 8.0F;
    private static final float KG_TO_LBS = 2.20462F;
    // --- Tuning knobs for the in-hand pose (blind-calibrated; flip signs if it looks off) ---
    /** Dangle: rotation standing the fish on its nose (degrees around X). */
    private static final float DANGLE_PITCH = 90.0F;
    /** Dangle: drop below the grip, in blocks (scaled). */
    private static final float DANGLE_DROP = 0.55F;
    /** Carry: roll laying the fish flat across the arms (degrees around Z). */
    private static final float CARRY_ROLL = 90.0F;
    /** Carry: shift toward the body center, in blocks. */
    private static final float CARRY_CENTER = 0.18F;

    private static final Map<Item, EntityType<?>> TYPE_BY_ITEM = new HashMap<>();
    private static final Map<EntityType<?>, Entity> DUMMIES = new HashMap<>();
    private static final Set<Entity> DISPLAY = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable private static Level dummyLevel;

    private FishCarryRenderer() {}

    /** True for the cached in-hand display entities, which other render hooks must skip. */
    public static boolean isDisplayEntity(Entity entity)
    {
        return DISPLAY.contains(entity);
    }

    /**
     * Renders the held fish as its entity; returns true when it did (the caller cancels the
     * vanilla item render), false to fall back to the ordinary item.
     */
    public static boolean render(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                                 boolean leftHand, PoseStack pose, MultiBufferSource buffer, int light)
    {
        if (!NiceCatchConfig.CLIENT.fishCarryEnabled.get()) return false;
        if (context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return false;
        }
        if (stack.isEmpty() || !isFishStack(stack)) return false;
        Entity display = displayEntity(stack.getItem());
        if (display == null) return false;

        Minecraft mc = Minecraft.getInstance();
        float lbs = heldWeightLbs(stack);
        float scale = displayScale(display, stack);
        float t = holder.tickCount + mc.getPartialTick();

        if (lbs < TWO_HAND_LBS) {
            // Dangling by the tail: nose-down under the grip, swaying gently.
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.25F) * 6.0F));
            pose.mulPose(Axis.XP.rotationDegrees(DANGLE_PITCH));
            pose.translate(0.0D, -DANGLE_DROP * scale, 0.0D);
        } else {
            // The two-armed heft: laid flat across the hands, shifted toward the middle.
            pose.translate((leftHand ? 1.0D : -1.0D) * CARRY_CENTER, -0.1D, 0.0D);
            pose.mulPose(Axis.ZP.rotationDegrees(leftHand ? -CARRY_ROLL : CARRY_ROLL));
            pose.translate(0.0D, -display.getBbHeight() * scale * 0.5D, 0.0D);
        }
        pose.scale(scale, scale, scale);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        dispatcher.render(display, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, pose, buffer, light);
        dispatcher.setRenderShadow(true);
        return true;
    }

    /** A fish in hand: anything the catch system stamped, or any item in the fishes tag. */
    private static boolean isFishStack(ItemStack stack)
    {
        return stack.getTagElement("NiceCatch") != null || stack.is(ItemTags.FISHES);
    }

    private static float heldWeightLbs(ItemStack stack)
    {
        CompoundTag info = stack.getTagElement("NiceCatch");
        if (info != null && info.contains("Weight", Tag.TAG_FLOAT)) {
            return info.getFloat("Weight") * KG_TO_LBS;
        }
        return 1.5F; // an un-stamped fish item: light, one hand
    }

    /** Model scale recovering the caught individual's body size from its stamped weight. */
    private static float displayScale(Entity display, ItemStack stack)
    {
        CompoundTag info = stack.getTagElement("NiceCatch");
        if (info == null || !info.contains("Weight", Tag.TAG_FLOAT)) return 1.0F;
        double baseVolume = display.getType().getWidth() * display.getType().getWidth()
                * display.getType().getHeight();
        if (baseVolume < 1.0E-4D) return 1.0F;
        double volume = info.getFloat("Weight") / NiceCatchConfig.SERVER.weightPerVolume.get();
        return Mth.clamp((float) Math.cbrt(volume / baseVolume), 0.4F, 2.8F);
    }

    /** The cached, never-ticked display entity for this fish item; null = no entity match. */
    @Nullable
    private static Entity displayEntity(Item item)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (dummyLevel != mc.level) {
            // Level changed: old dummies hold the old level; start fresh.
            DUMMIES.clear();
            DISPLAY.clear();
            TYPE_BY_ITEM.clear();
            dummyLevel = mc.level;
        }
        EntityType<?> type = typeFor(item);
        if (type == null) return null;
        Entity cached = DUMMIES.get(type);
        if (cached != null) return cached;
        Entity created = type.create(mc.level);
        if (created == null || !FishBehavior.isFishKind(created)) {
            if (created != null) created.discard();
            TYPE_BY_ITEM.put(item, null); // negative-cache: not a renderable fish
            return null;
        }
        DUMMIES.put(type, created);
        DISPLAY.add(created);
        return created;
    }

    /** Item -> entity by the shared registry-name convention (minecraft:cod is both, etc). */
    @Nullable
    private static EntityType<?> typeFor(Item item)
    {
        if (TYPE_BY_ITEM.containsKey(item)) return TYPE_BY_ITEM.get(item);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        // containsKey guard matters: the entity registry answers unknown ids with its
        // default entry (a pig), and nobody wants to carry that.
        EntityType<?> type = id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id)
                ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
        TYPE_BY_ITEM.put(item, type);
        return type;
    }
}
