plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

stonecutter parameters {
    filters.include("**/com/pathmind/screen/*.java")
    constants {
        put("MC_1_21_8", current.version == "1.21")
        put("MC_1_21_10", current.version == "1.21.10")
        put("PRE_1_21_11", current.version == "1.21" || current.version == "1.21.10")
        put("MC_26", current.version.startsWith("26."))
    }
}
