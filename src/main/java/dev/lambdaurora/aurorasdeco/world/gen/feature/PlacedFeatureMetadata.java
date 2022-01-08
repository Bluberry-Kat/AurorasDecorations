/*
 * Copyright (c) 2021 - 2022 LambdAurora <aurora42lambda@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.lambdaurora.aurorasdeco.world.gen.feature;

import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents a registrable {@link PlacedFeature} to which metadata is assigned to ease biome selection.
 */
public class PlacedFeatureMetadata {
	private final RegistryKey<PlacedFeature> key;
	private final PlacedFeature feature;
	private final List<Biome.Category> allowedCategories = new ArrayList<>();
	private final List<RegistryKey<ConfiguredFeature<?, ?>>> allowedNeighborFeatures = new ArrayList<>();
	private Tag<Biome> allowedTag;

	public PlacedFeatureMetadata(RegistryKey<PlacedFeature> key, PlacedFeature feature) {
		this.key = key;
		this.feature = feature;
	}

	public RegistryKey<PlacedFeature> getKey() {
		return this.key;
	}

	public PlacedFeature getFeature() {
		return this.feature;
	}

	public PlacedFeatureMetadata addAllowedBiomeCategory(Biome.Category... categories) {
		this.allowedCategories.addAll(Arrays.asList(categories));
		return this;
	}

	public Biome.Category[] getAllowedBiomeCategories() {
		return this.allowedCategories.toArray(Biome.Category[]::new);
	}

	public Predicate<BiomeSelectionContext> getAllowedBiomeCategoriesPredicate() {
		if (this.allowedCategories.isEmpty())
			return biomeSelectionContext -> true;
		return BiomeSelectors.categories(this.getAllowedBiomeCategories());
	}

	public PlacedFeatureMetadata addAllowedNeighborFeature(Identifier key) {
		return this.addAllowedNeighborFeature(RegistryKey.of(Registry.CONFIGURED_FEATURE_KEY, key));
	}

	public PlacedFeatureMetadata addAllowedNeighborFeature(RegistryKey<ConfiguredFeature<?, ?>> key) {
		this.allowedNeighborFeatures.add(key);
		return this;
	}

	public Predicate<BiomeSelectionContext> getAllowedNeighborFeaturesPredicate() {
		if (this.allowedNeighborFeatures.isEmpty())
			return biomeSelectionContext -> true;

		return biomeSelectionContext -> {
			for (var feature : this.allowedNeighborFeatures) {
				if (biomeSelectionContext.hasFeature(feature)) {
					return true;
				}
			}

			return false;
		};
	}

	public PlacedFeatureMetadata setAllowedTag(Tag<Biome> tag) {
		this.allowedTag = tag;
		return this;
	}

	public Predicate<BiomeSelectionContext> getTagPredicate() {
		if (this.allowedTag != null)
			return BiomeSelectors.tag(this.allowedTag);
		else
			return biomeSelectionContext -> false;
	}

	public Predicate<BiomeSelectionContext> getBiomeSelectionPredicate() {
		return this.getAllowedBiomeCategoriesPredicate()
				.and(this.getAllowedNeighborFeaturesPredicate())
				.or(this.getTagPredicate());
	}

	public PlacedFeatureMetadata register() {
		Registry.register(BuiltinRegistries.PLACED_FEATURE, this.key, this.feature);
		return this;
	}
}
