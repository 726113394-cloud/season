package io.Sriptirc_wp_1258.seasonalplugin.season;

/**
 * 四季枚举
 */
public enum Season {
    SPRING("春", "§a"),
    SUMMER("夏", "§e"),
    AUTUMN("秋", "§6"),
    WINTER("冬", "§b");

    private final String displayName;
    private final String color;

    Season(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    /**
     * 带颜色格式的显示名
     */
    public String getColoredName() {
        return color + displayName + "§r";
    }

    /**
     * 获取下一个季节
     */
    public Season next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
