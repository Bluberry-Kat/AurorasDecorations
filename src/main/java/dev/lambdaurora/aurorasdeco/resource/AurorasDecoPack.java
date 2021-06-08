/*
 * Copyright (c) 2021 LambdAurora <aurora42lambda@gmail.com>
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

package dev.lambdaurora.aurorasdeco.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import dev.lambdaurora.aurorasdeco.AurorasDeco;
import dev.lambdaurora.aurorasdeco.block.BenchBlock;
import dev.lambdaurora.aurorasdeco.block.ShelfBlock;
import dev.lambdaurora.aurorasdeco.block.StumpBlock;
import dev.lambdaurora.aurorasdeco.block.big_flower_pot.BigFlowerPotBlock;
import dev.lambdaurora.aurorasdeco.block.big_flower_pot.PottedPlantType;
import dev.lambdaurora.aurorasdeco.registry.AurorasDecoRegistry;
import dev.lambdaurora.aurorasdeco.resource.datagen.LangBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AurorasDecoPack implements ModResourcePack {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Set<String> namespaces = new HashSet<>();
    private final Map<String, byte[]> resources = new Object2ObjectOpenHashMap<>();
    private final ResourceType type;

    private boolean hasRegisteredOneTimeResources = false;

    public AurorasDecoPack(ResourceType type) {
        this.type = type;
    }

    public AurorasDecoPack rebuild(ResourceType type) {
        return type == ResourceType.CLIENT_RESOURCES ? this.rebuildClient() : this.rebuildData();
    }

    public AurorasDecoPack rebuildClient() {
        var langBuilder = new LangBuilder();
        langBuilder.load();

        this.namespaces.add("aurorasdeco");
        var baseBigFlowerPotJson = new JsonObject();

        {
            var variant = new JsonObject();
            variant.addProperty("model", AurorasDeco.NAMESPACE + ":block/big_flower_pot/big_flower_pot");
            var variants = new JsonObject();
            variants.add("", variant);
            baseBigFlowerPotJson.add("variants", variants);
        }

        PottedPlantType.stream().filter(type -> !type.isEmpty() && type.getPot().hasDynamicModel())
                .forEach(type -> {
                    var id = Registry.BLOCK.getId(type.getPot());
                    this.putJson("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json",
                            baseBigFlowerPotJson);

                    Datagen.registerBetterGrassLayer(id, BigFlowerPotBlock.POT_BETTERGRASS_DATA);
                });

        Datagen.generateClientData(langBuilder);

        langBuilder.write(this);

        return this;
    }

    private void registerTag(String[] types, Identifier id, Stream<Identifier> entries) {
        var root = new JsonObject();
        root.addProperty("replace", false);
        var values = new JsonArray();

        entries.forEach(value -> values.add(value.toString()));

        root.add("values", values);

        for (var type : types) {
            this.putJson("data/" + id.getNamespace() + "/tags/" + type + "/" + id.getPath() + ".json",
                    root);
        }
    }

    public AurorasDecoPack rebuildData() {
        this.resources.clear();
        this.namespaces.clear();

        if (!this.hasRegisteredOneTimeResources) {
            Datagen.registerDefaultRecipes();
            Datagen.registerDefaultWoodcuttingRecipes();
            this.hasRegisteredOneTimeResources = true;
        }

        BenchBlock.streamBenches().forEach(Datagen::registerBenchBlockLootTable);
        ShelfBlock.streamShelves().forEach(Datagen::registerShelfBlockLootTable);
        StumpBlock.streamLogStumps().forEach(Datagen::dropsSelf);

        this.registerTag(new String[]{"blocks", "items"}, AurorasDeco.id("benches"), BenchBlock.streamBenches()
                .map(Registry.BLOCK::getId));
        this.registerTag(new String[]{"blocks", "items"}, AurorasDeco.id("shelves"), ShelfBlock.streamShelves()
                .map(Registry.BLOCK::getId));
        this.registerTag(new String[]{"blocks", "items"}, AurorasDeco.id("stumps"), StumpBlock.streamLogStumps()
                .map(Registry.BLOCK::getId));

        Datagen.registerSimpleRecipesUnlock();

        LOGGER.info("Registered " + this.resources.size() + " resources.");

        return this;
    }

    public void putResource(String resource, byte[] data) {
        this.resources.put(resource, data);

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            try {
                var path = Paths.get("debug", "aurorasdeco").resolve(resource);
                Files.createDirectories(path.getParent());
                Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void putJson(ResourceType type, Identifier id, JsonObject json) {
        this.namespaces.add(id.getNamespace());

        String path = Datagen.toPath(id, type) + ".json";
        this.putJson(path, json);
    }

    public void putJson(String resource, JsonObject json) {
        var stringWriter = new StringWriter();
        var jsonWriter = new JsonWriter(stringWriter);
        jsonWriter.setLenient(true);
        jsonWriter.setIndent("  ");
        try {
            Streams.write(json, jsonWriter);
        } catch (IOException e) {
            LOGGER.error("Failed to write JSON at {}.", resource, e);
        }
        this.putResource(resource, stringWriter.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ModMetadata getFabricModMetadata() {
        return FabricLoader.getInstance().getModContainer(AurorasDeco.NAMESPACE).get().getMetadata();
    }

    @Override
    public @Nullable InputStream openRoot(String fileName) throws IOException {
        if (ModResourcePackUtil.containsDefault(this.getFabricModMetadata(), fileName)) {
            return ModResourcePackUtil.openDefault(this.getFabricModMetadata(),
                    this.type,
                    fileName);
        }

        byte[] data;
        if ((data = this.resources.get(fileName)) != null) {
            return new ByteArrayInputStream(data);
        }
        throw new IOException("Generated resources pack has no data or alias for " + fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        if (type != this.type)
            throw new IOException("Reading data from the wrong resource pack.");
        return this.openRoot(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
    }

    @Override
    public Collection<Identifier> findResources(ResourceType type, String namespace, String prefix, int maxDepth,
                                                Predicate<String> pathFilter) {
        if (type != this.type) return Collections.emptyList();
        var start = type.getDirectory() + "/" + namespace + "/" + prefix;
        return this.resources.keySet().stream()
                .filter(s -> s.startsWith(start) && pathFilter.test(s))
                .map(AurorasDecoPack::fromPath)
                .collect(Collectors.toList());
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        var path = type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath();
        return this.resources.containsKey(path);
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return this.namespaces;
    }

    @Nullable
    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) throws IOException {
        InputStream inputStream = this.openRoot("pack.mcmeta");
        Throwable error = null;

        T metadata;
        try {
            metadata = AbstractFileResourcePack.parseMetadata(metaReader, inputStream);
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            if (inputStream != null) {
                if (error != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable e) {
                        error.addSuppressed(e);
                    }
                } else {
                    inputStream.close();
                }
            }
        }

        return metadata;
    }

    @Override
    public String getName() {
        return "Aurora's Decorations Virtual Pack";
    }

    @Override
    public void close() {
        if (this.type == ResourceType.CLIENT_RESOURCES) {
            this.resources.clear();
            this.namespaces.clear();
        }
    }

    private static Identifier fromPath(String path) {
        String[] split = path.replaceAll("((assets)|(data))/", "").split("/", 2);

        return new Identifier(split[0], split[1]);
    }
}
