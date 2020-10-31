package nl.aurorion.blockregen.system.preset.struct.material;

import nl.aurorion.blockregen.BlockRegen;
import nl.aurorion.blockregen.ParseUtil;
import nl.aurorion.blockregen.Utils;
import com.google.common.base.Strings;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DynamicMaterial {

    private boolean fixed = false;

    private final List<Material> valuedMaterials = new ArrayList<>();

    private Material defaultMaterial;

    public DynamicMaterial(String input) {

        if (Strings.isNullOrEmpty(input))
            throw new IllegalArgumentException("Input string cannot be null");

        input = input.replace(" ", "").trim().toUpperCase();

        List<String> materials;

        if (input.contains(";")) {
            materials = new ArrayList<>(Arrays.asList(input.split(";")));
        } else {
            defaultMaterial = ParseUtil.parseMaterial(input, true);

            if (defaultMaterial == null)
                throw new IllegalArgumentException("Invalid block material");

            fixed = true;
            return;
        }

        if (materials.isEmpty())
            throw new IllegalArgumentException("Dynamic material doesn't have the correct syntax");

        else if (materials.size() == 1) {
            defaultMaterial = ParseUtil.parseMaterial(materials.get(0), true);

            if (defaultMaterial == null)
                throw new IllegalArgumentException("Invalid block material");

            fixed = true;
            return;
        }

        int total = 0;

        for (String material : materials) {

            if (!material.contains(":")) {
                defaultMaterial = ParseUtil.parseMaterial(material, true);

                if (defaultMaterial == null)
                    throw new IllegalArgumentException("Invalid block material");

                continue;
            }

            int chance = Integer.parseInt(material.split(":")[1]);
            total += chance;

            for (int i = 0; i < chance; i++) {
                Material mat = ParseUtil.parseMaterial(material.split(":")[0], true);

                if (mat == null) {
                    BlockRegen.getInstance().getConsoleOutput().debug("Invalid material " + material.split(":")[0] + " skipped");
                    continue;
                }

                valuedMaterials.add(mat);
            }
        }

        if (defaultMaterial != null) {
            for (int i = 0; i < (100 - total); i++) valuedMaterials.add(defaultMaterial);
        }
    }

    @NotNull
    public Material get() {
        if (fixed) return defaultMaterial;
        Material pickedMaterial = valuedMaterials.get(BlockRegen.getInstance().getRandom().nextInt(valuedMaterials.size()));
        return pickedMaterial != null ? pickedMaterial : defaultMaterial;
    }
}