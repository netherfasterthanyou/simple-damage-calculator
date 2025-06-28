package com.combat.calculator.util;

import com.combat.calculator.CombatCalculatorMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

/**
 * Advanced damage calculator
 * Handles complex damage calculations with multiple factors
 */
@Mod.EventBusSubscriber(modid = CombatCalculatorMod.MODID)
public class DamageCalculator {

    // Base damage modifiers (balanced with negatives)
    private static final double CRITICAL_HIT_MULTIPLIER = 1.35; // 35% bonus
    private static final double BACKSTAB_MULTIPLIER = 1.25; // 25% bonus
    private static final double FRONTAL_ATTACK_PENALTY = 0.95; // 5% penalty for frontal attacks
    private static final double ELEMENTAL_WEAKNESS_MULTIPLIER = 1.1; // 10% bonus
    private static final double ELEMENTAL_RESISTANCE_MULTIPLIER = 0.9; // 10% penalty

    // Attack speed thresholds for damage scaling
    private static final double SLOW_ATTACK_THRESHOLD = 1.0;
    private static final double FAST_ATTACK_THRESHOLD = 2.5;

    // Environmental modifiers
    private static final float RAIN_PENALTY = 0.95f; // 5% penalty in rain
    private static final float UNDERWATER_PENALTY = 0.85f; // 15% penalty underwater
    private static final float HIGH_ALTITUDE_BONUS = 1.05f; // 5% bonus above Y=128
    private static final float NETHER_HEAT_PENALTY = 0.95f; // 5% penalty in hot biomes

    /**
     * Event handler that intercepts damage and applies custom calculations
     * This automatically triggers for all living entity damage
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource damageSource = event.getSource();
        Entity sourceEntity = damageSource.getEntity();

        // Only process damage from living entities (players, mobs)
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        // Skip if damage is already modified by other mods to prevent conflicts
        if (event.getAmount() <= 0) {
            return;
        }

        // Get the weapon used (if any)
        ItemStack weapon = ItemStack.EMPTY;
        if (attacker instanceof Player player) {
            weapon = player.getMainHandItem();
        }

        float originalDamage = event.getAmount();
        float customDamage = calculateDamage(attacker, target, weapon, damageSource);

        // Apply the new damage amount
        event.setAmount(customDamage);

        if (attacker instanceof Player player && player.isCreative()) {
            // Show damage breakdown to creative players
            DamageBreakdown breakdown = getDamageBreakdown(attacker, target, weapon);
        }

        handleSpecialDamageEffects(attacker, target, weapon, customDamage);
    }

    /**
     * Calculate total damage dealt by attacker to target
     * Balanced to be close to vanilla with small improvements
     */
    public static float calculateDamage(LivingEntity attacker, LivingEntity target, ItemStack weapon, DamageSource damageSource) {
        // Get base weapon damage (this should be close to vanilla)
        float baseDamage = getBaseDamage(weapon, attacker);

        // Apply small attack speed scaling (much more subtle)
        float attackSpeedModifier = getAttackSpeedModifier(weapon, attacker);
        baseDamage *= attackSpeedModifier;

        // Apply small attribute bonuses
        float attributeBonus = getAttributeDamageBonus(attacker);
        baseDamage += attributeBonus * 0.5f; // Reduce attribute impact

        // Apply enchantment bonuses (vanilla-like)
        float enchantmentBonus = getEnchantmentDamage(weapon, target);
        baseDamage += enchantmentBonus * 0.8f; // Slightly reduce enchantment power

        // Apply critical hit (small bonus)
        boolean isCritical = shouldCriticalHit(attacker, target);
        if (isCritical) {
            baseDamage *= (float) CRITICAL_HIT_MULTIPLIER;
        }

        // Apply positional modifiers (much smaller)
        float positionalModifier = getPositionalModifier(attacker, target);
        baseDamage *= positionalModifier;

        // Apply environmental and condition penalties/bonuses
        float environmentalModifier = getEnvironmentalModifier(attacker, target);
        baseDamage *= environmentalModifier;

        // Apply weapon condition penalties
        float weaponConditionModifier = getWeaponConditionModifier(weapon);
        baseDamage *= weaponConditionModifier;

        // Apply fatigue/hunger penalties for players
        float playerConditionModifier = getPlayerConditionModifier(attacker);
        baseDamage *= playerConditionModifier;

        // Apply small elemental damage
        float elementalDamage = calculateElementalDamage(weapon, target);
        baseDamage += elementalDamage * 0.6f; // Reduce elemental damage

        // Apply status effect modifiers (smaller impact)
        float statusModifier = getStatusEffectModifier(attacker, target);
        baseDamage *= statusModifier;

        float finalDamage = applyArmorReduction(baseDamage, target);

        finalDamage = applyRandomVariance(finalDamage);


        finalDamage = Math.min(finalDamage, target.getMaxHealth());

        return Math.max(0.45f, finalDamage); // Minimum 30% damage in worst conditions
    }

    /**
     * Handle special effects based on damage calculations
     */
    private static void handleSpecialDamageEffects(LivingEntity attacker, LivingEntity target, ItemStack weapon, float damage) {
        // Critical hit effects
        if (shouldCriticalHit(attacker, target)) {
            if (attacker instanceof Player player) {
                player.level().addParticle(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        0.0, 0.0, 0.0);
            }
        }

        float positionalMod = getPositionalModifier(attacker, target);
        if (positionalMod >= BACKSTAB_MULTIPLIER) {
            // Add backstab effects - particles, sounds, extra effects
            if (attacker instanceof Player player) {
                player.level().addParticle(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        0.0, 0.0, 0.0);
            }
        }

        // High damage threshold effects (more reasonable threshold)
        if (damage > target.getMaxHealth() * 0.3f) {
            if (attacker instanceof Player player) {
                player.level().addParticle(ParticleTypes.FLASH,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        0.0, 0.0, 0.0);
            }
        }

        // Elemental damage effects
        float elementalDamage = calculateElementalDamage(weapon, target);
        if (elementalDamage > 0) {
            // Apply elemental status effects
            int fireAspectLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.FIRE_ASPECT, weapon);
            if (fireAspectLevel > 0) {
                target.setSecondsOnFire(fireAspectLevel * 4);
            }
        }
    }

    /**
     * Get base weapon damage including item attack damage attribute
     */
    private static float getBaseDamage(ItemStack weapon, LivingEntity attacker) {
        if (weapon.isEmpty()) {
            return 1.0f; // Base hand damage
        }

        // Get weapon's attack damage attribute
        double attackDamage = weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)
                .stream()
                .mapToDouble(AttributeModifier::getAmount)
                .sum();

        return (float) Math.max(1.0, attackDamage);
    }

    /**
     * Calculate attack speed modifier for damage scaling (subtle changes)
     */
    private static float getAttackSpeedModifier(ItemStack weapon, LivingEntity attacker) {
        if (weapon.isEmpty()) {
            return 1.0f;
        }

        double attackSpeed = weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_SPEED)
                .stream()
                .mapToDouble(AttributeModifier::getAmount)
                .sum();

        // Normalize attack speed (4.0 is base for most weapons)
        attackSpeed += 4.0;

        if (attackSpeed <= SLOW_ATTACK_THRESHOLD) {
            // Slow weapons get small damage bonus
            return 1.15f; // Increased from 1.1f
        } else if (attackSpeed >= FAST_ATTACK_THRESHOLD) {
            // Fast weapons get small damage penalty
            return 0.85f; // Increased penalty from 0.95f
        }

        return 1.0f; // Normal speed weapons
    }

    /**
     * Get damage bonus from entity attributes (strength, etc.)
     */
    private static float getAttributeDamageBonus(LivingEntity attacker) {
        double attackDamageAttribute = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (float) (attackDamageAttribute - 1.0); // Subtract base value
    }

    /**
     * Calculate enchantment damage bonuses
     */
    private static float getEnchantmentDamage(ItemStack weapon, LivingEntity target) {
        float bonus = 0.0f;

        // Sharpness
        int sharpnessLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SHARPNESS, weapon);
        if (sharpnessLevel > 0) {
            bonus += 0.5f + (sharpnessLevel * 0.5f);
        }

        // Smite (vs undead)
        int smiteLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SMITE, weapon);
        if (smiteLevel > 0 && target.getMobType().equals(net.minecraft.world.entity.MobType.UNDEAD)) {
            bonus += smiteLevel * 2.5f;
        }

        // Bane of Arthropods (vs arthropods)
        int baneLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BANE_OF_ARTHROPODS, weapon);
        if (baneLevel > 0 && target.getMobType().equals(net.minecraft.world.entity.MobType.ARTHROPOD)) {
            bonus += baneLevel * 2.5f;
        }

        return bonus;
    }

    /**
     * Determine if attack should be a critical hit
     */
    private static boolean shouldCriticalHit(LivingEntity attacker, LivingEntity target) {
        if (attacker instanceof Player player) {
            // Player critical hit conditions
            return player.getAttackStrengthScale(0.5f) > 0.9f &&
                    player.fallDistance > 0.0f &&
                    !player.onGround() &&
                    !player.isInWater() &&
                    !player.hasEffect(MobEffects.BLINDNESS) &&
                    player.getVehicle() == null;
        }

        // Random critical hit chance for mobs
        return attacker.getRandom().nextFloat() < 0.05f; // 5% chance
    }

    /**
     * Calculate positional damage modifiers (much more balanced)
     */
    private static float getPositionalModifier(LivingEntity attacker, LivingEntity target) {
        float modifier = 1.0f;

        // Calculate angle between attacker and target's facing direction
        double attackerYaw = Math.atan2(attacker.getZ() - target.getZ(), attacker.getX() - target.getX());
        double targetYaw = Math.toRadians(target.getYRot());
        double angleDiff = Math.abs(attackerYaw - targetYaw);

        // Normalize angle
        while (angleDiff > Math.PI) {
            angleDiff -= 2 * Math.PI;
        }
        angleDiff = Math.abs(angleDiff);

        // Backstab (within 45 degrees of back) - good bonus
        if (angleDiff < Math.PI / 4) {
            modifier *= (float) BACKSTAB_MULTIPLIER; // 15% bonus
        }
        // Side attack bonus (45-135 degrees) - small bonus
        else if (angleDiff < 3 * Math.PI / 4) {
            modifier *= 1.08f; // 8% bonus
        }
        // Frontal attack (within 45 degrees of front) - penalty!
        else {
            modifier *= (float) FRONTAL_ATTACK_PENALTY; // 5% penalty for head-on attacks
        }

        return modifier;
    }

    /**
     * Calculate environmental damage modifiers based on world conditions
     */
    private static float getEnvironmentalModifier(LivingEntity attacker, LivingEntity target) {
        float modifier = 1.0f;
        Level level = attacker.level();
        BlockPos pos = attacker.blockPosition();

        // Rain penalty - weapons are harder to grip, visibility reduced
        if (level.isRaining() && level.canSeeSky(pos)) {
            modifier *= RAIN_PENALTY;
        }

        // Underwater combat penalty - movement is restricted
        if (attacker.isInWater() && !attacker.hasEffect(MobEffects.WATER_BREATHING)) {
            modifier *= UNDERWATER_PENALTY;
        }

        // High altitude bonus - thinner air, less resistance
        if (pos.getY() > 128) {
            modifier *= HIGH_ALTITUDE_BONUS;
        }

        // Hot biome penalty - fatigue from heat
        Biome biome = level.getBiome(pos).value();
        if (biome.getBaseTemperature() > 1.5f) { // Very hot biomes
            modifier *= NETHER_HEAT_PENALTY;
        }

        // Darkness penalty - reduced accuracy
        int lightLevel = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        if (lightLevel < 4 && !attacker.hasEffect(MobEffects.NIGHT_VISION)) {
            modifier *= 0.95f; // 5% penalty in darkness
        }

        return modifier;
    }

    /**
     * Calculate weapon condition modifier based on durability
     */
    private static float getWeaponConditionModifier(ItemStack weapon) {
        if (weapon.isEmpty() || !weapon.isDamageableItem()) {
            return 1.0f;
        }

        int maxDurability = weapon.getMaxDamage();
        int currentDamage = weapon.getDamageValue();
        float durabilityPercent = 1.0f - ((float) currentDamage / (float) maxDurability);

        // Weapons lose effectiveness as they break down
        if (durabilityPercent < 0.1f) {
            return 0.85f; // 15% penalty when almost broken
        } else if (durabilityPercent < 0.25f) {
            return 0.92f; // 8% penalty when heavily damaged
        } else if (durabilityPercent < 0.5f) {
            return 0.97f; // 3% penalty when moderately damaged
        }

        return 1.0f; // No penalty for good condition weapons
    }

    /**
     * Calculate player condition modifier based on hunger and fatigue
     */
    private static float getPlayerConditionModifier(LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return 1.0f;
        }

        float modifier = 1.0f;

        // Hunger penalty
        int foodLevel = player.getFoodData().getFoodLevel();
        if (foodLevel < 6) {
            modifier *= 0.85f; // 15% penalty when very hungry
        } else if (foodLevel < 12) {
            modifier *= 0.95f; // 5% penalty when hungry
        }

        // Exhaustion penalty
        float exhaustion = player.getFoodData().getExhaustionLevel();
        if (exhaustion > 3.0f) {
            modifier *= 0.93f; // 7% penalty when exhausted
        }

        return modifier;
    }

    /**
     * Calculate elemental damage based on weapon properties (balanced)
     */
    private static float calculateElementalDamage(ItemStack weapon, LivingEntity target) {
        float elementalDamage = 0.0f;

        int fireAspectLevel = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.FIRE_ASPECT, weapon);
        if (fireAspectLevel > 0) {
            elementalDamage += fireAspectLevel * 0.5f;
            // Extra damage if target is vulnerable to fire
            if (isVulnerableToFire(target)) {
                elementalDamage *= (float) ELEMENTAL_WEAKNESS_MULTIPLIER; // 10% bonus
            } else if (isResistantToFire(target)) {
                elementalDamage *= (float) ELEMENTAL_RESISTANCE_MULTIPLIER; // 10% penalty
            }
        }


        return elementalDamage;
    }

    /**
     * Check if entity is vulnerable to fire damage
     */
    private static boolean isVulnerableToFire(LivingEntity entity) {
        // Add logic for entities vulnerable to fire
        // Ice-based mobs, plant-based mobs, etc.
        return entity.getMobType().equals(net.minecraft.world.entity.MobType.UNDEAD) ||
                entity.getType().toString().contains("creeper") ||
                entity.getType().toString().contains("ice") ||
                entity.getType().toString().contains("snow");
    }

    /**
     * Check if entity is resistant to fire damage
     */
    private static boolean isResistantToFire(LivingEntity entity) {
        // Nether mobs, fire-based entities, etc.
        return entity.fireImmune() ||
                entity.getType().toString().contains("blaze") ||
                entity.getType().toString().contains("magma") ||
                entity.getType().toString().contains("ghast") ||
                entity.hasEffect(MobEffects.FIRE_RESISTANCE);
    }

    /**
     * Apply status effect modifiers to damage (more balanced)
     */
    private static float getStatusEffectModifier(LivingEntity attacker, LivingEntity target) {
        float modifier = 1.0f;

        // Attacker status effects
        if (attacker.hasEffect(MobEffects.DAMAGE_BOOST)) {
            MobEffectInstance effect = attacker.getEffect(MobEffects.DAMAGE_BOOST);
            assert effect != null;
            modifier += 0.15f * (effect.getAmplifier() + 1);
        }

        if (attacker.hasEffect(MobEffects.WEAKNESS)) {
            MobEffectInstance effect = attacker.getEffect(MobEffects.WEAKNESS);
            assert effect != null;
            modifier -= 0.25f * (effect.getAmplifier() + 1);
        }

        // Mining Fatigue affects combat too
        if (attacker.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            MobEffectInstance effect = attacker.getEffect(MobEffects.DIG_SLOWDOWN);
            assert effect != null;
            modifier *= (1.0f - (0.1f * (effect.getAmplifier() + 1)));
        }

        // Haste slightly improves combat
        if (attacker.hasEffect(MobEffects.DIG_SPEED)) {
            MobEffectInstance effect = attacker.getEffect(MobEffects.DIG_SPEED);
            assert effect != null;
            modifier += 0.05f * (effect.getAmplifier() + 1);
        }

        // Target status effects
        if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            MobEffectInstance effect = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            assert effect != null;
            modifier *= (1.0f - (0.15f * (effect.getAmplifier() + 1)));
        }

        // Wither effect makes target more vulnerable
        if (target.hasEffect(MobEffects.WITHER)) {
            modifier *= 1.1f; // 10% more damage when withering
        }

        // Poison makes target slightly more vulnerable
        if (target.hasEffect(MobEffects.POISON)) {
            modifier *= 1.05f; // 5% more damage when poisoned
        }

        return Math.max(0.45f, modifier); // Minimum 45% damage
    }

    /**
     * Apply armor damage reduction
     */
    private static float applyArmorReduction(float damage, LivingEntity target) {
        float armor = (float) target.getAttributeValue(Attributes.ARMOR);
        float armorToughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        // Minecraft's armor calculation
        float f = 2.0f + armorToughness / 4.0f;
        float f1 = Math.min(armor - damage / f, armor * 0.2f);

        return damage * (1.0f - f1 / 25.0f);
    }

    /**
     * Apply random damage variance (smaller range)
     */
    private static float applyRandomVariance(float damage) {
        RandomSource random = RandomSource.create();
        float variance = 0.15f;
        float multiplier = 1.0f + (random.nextFloat() - 0.5f) * 2 * variance;
        return damage * multiplier;
    }

    /**
     * Calculate DPS (Damage Per Second) for weapon comparison
     */
    public static double calculateDPS(ItemStack weapon, LivingEntity wielder) {
        float baseDamage = getBaseDamage(weapon, wielder);

        double attackSpeed = 4.0; // Default attack speed
        if (!weapon.isEmpty()) {
            attackSpeed += weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                    .get(Attributes.ATTACK_SPEED)
                    .stream()
                    .mapToDouble(AttributeModifier::getAmount)
                    .sum();
        }

        return baseDamage * attackSpeed;
    }

    /**
     * Get damage breakdown for debugging/UI display
     */
    public static DamageBreakdown getDamageBreakdown(LivingEntity attacker, LivingEntity target, ItemStack weapon) {
        DamageBreakdown breakdown = new DamageBreakdown();

        breakdown.baseDamage = getBaseDamage(weapon, attacker);
        breakdown.attackSpeedModifier = getAttackSpeedModifier(weapon, attacker);
        breakdown.attributeBonus = getAttributeDamageBonus(attacker);
        breakdown.enchantmentBonus = getEnchantmentDamage(weapon, target);
        breakdown.isCritical = shouldCriticalHit(attacker, target);
        breakdown.positionalModifier = getPositionalModifier(attacker, target);
        breakdown.environmentalModifier = getEnvironmentalModifier(attacker, target);
        breakdown.weaponConditionModifier = getWeaponConditionModifier(weapon);
        breakdown.playerConditionModifier = getPlayerConditionModifier(attacker);
        breakdown.elementalDamage = calculateElementalDamage(weapon, target);
        breakdown.statusModifier = getStatusEffectModifier(attacker, target);

        float totalBeforeArmor = breakdown.baseDamage * breakdown.attackSpeedModifier +
                breakdown.attributeBonus + breakdown.enchantmentBonus + breakdown.elementalDamage;
        if (breakdown.isCritical) totalBeforeArmor *= (float) CRITICAL_HIT_MULTIPLIER;
        totalBeforeArmor *= breakdown.positionalModifier * breakdown.environmentalModifier *
                breakdown.weaponConditionModifier * breakdown.playerConditionModifier * breakdown.statusModifier;

        breakdown.finalDamage = applyArmorReduction(totalBeforeArmor, target);
        breakdown.finalDamage = applyRandomVariance(breakdown.finalDamage);

        return breakdown;
    }

    /**
     * Data class for damage calculation breakdown
     */
    public static class DamageBreakdown {
        public float baseDamage;
        public float attackSpeedModifier;
        public float attributeBonus;
        public float enchantmentBonus;
        public boolean isCritical;
        public float positionalModifier;
        public float environmentalModifier;
        public float weaponConditionModifier;
        public float playerConditionModifier;
        public float elementalDamage;
        public float statusModifier;
        public float finalDamage;

        @Override
        public String toString() {
            return String.format(
                    "Damage Breakdown:\n" +
                            "Base: %.1f\n" +
                            "Attack Speed Mod: %.2fx\n" +
                            "Attribute Bonus: +%.1f\n" +
                            "Enchantment Bonus: +%.1f\n" +
                            "Critical Hit: %s\n" +
                            "Position Modifier: %.2fx\n" +
                            "Environmental Mod: %.2fx\n" +
                            "Weapon Condition: %.2fx\n" +
                            "Player Condition: %.2fx\n" +
                            "Elemental Damage: +%.1f\n" +
                            "Status Modifier: %.2fx\n" +
                            "Final Damage: %.1f",
                    baseDamage, attackSpeedModifier, attributeBonus, enchantmentBonus,
                    isCritical ? "Yes" : "No", positionalModifier, environmentalModifier,
                    weaponConditionModifier, playerConditionModifier, elementalDamage,
                    statusModifier, finalDamage
            );
        }
    }
}