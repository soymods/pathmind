plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

stonecutter parameters {
    filters.include("**/com/pathmind/screen/*.java")
    constants {
        put("MC_26", current.version == "26.1")
    }
}
