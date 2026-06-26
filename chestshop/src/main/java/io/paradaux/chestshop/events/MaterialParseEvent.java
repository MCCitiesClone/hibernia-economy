package io.paradaux.chestshop.events;

import org.bukkit.Material;

/**
 * Mutable carrier for resolving the material part of an item code, used by
 * {@link io.paradaux.chestshop.services.ItemService#parseMaterial}. Formerly a Bukkit event.
 */
public class MaterialParseEvent {

    private final String materialString;
    private final short data;
    private Material material = null;

    public MaterialParseEvent(String materialString, @Deprecated short data) {
        this.materialString = materialString;
        this.data = data;
    }

    /**
     * Get the material string that should be parsed
     * @return The material string to parse
     */
    public String getMaterialString() {
        return materialString;
    }

    /**
     * Get the data of legacy materials that might result in different flattening materials
     * @return The data
     * @deprecated Modern materials don't use data values anymore
     */
    @Deprecated
    public short getData() {
        return data;
    }

    /**
     * Set the material that the string represents
     * @param material The material for the string
     */
    public void setMaterial(Material material) {
        this.material = material;
    }

    /**
     * The material that was parsed
     * @return The parsed material or null if none was found
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Whether or not the material string of this event has a parsed material
     * @return True if an material was successfully parsed; false if not
     */
    public boolean hasMaterial() {
        return material != null;
    }

}
