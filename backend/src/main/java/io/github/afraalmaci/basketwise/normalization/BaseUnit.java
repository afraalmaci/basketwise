package io.github.afraalmaci.basketwise.normalization;

/**
 * The unit every quantity is normalized to. Amounts are stored as whole numbers
 * of this unit (e.g. 1.5 L is stored as 1500 MILLILITER).
 */
public enum BaseUnit {
    MILLILITER("ml"),
    GRAM("g"),
    PIECE("pc");

    private final String code;

    BaseUnit(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static BaseUnit fromCode(String code) {
        for (BaseUnit unit : values()) {
            if (unit.code.equals(code)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit code: " + code);
    }

    /**
     * How many base units make up one "reference" unit for unit pricing:
     * price per liter, per kilogram, or per piece.
     */
    public long referenceSize() {
        return this == PIECE ? 1 : 1000;
    }
}
