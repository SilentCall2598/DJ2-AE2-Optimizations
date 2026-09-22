package dj2.ae2opt.core;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.Map;


public final class CompatibilityCheck {

    public static final String AE2_MOD_ID = "appliedenergistics2";
    public static final String DRAWERS_MOD_ID = "storagedrawers";
    public static final String ENDER_UTILITIES_MOD_ID = "enderutilities";
    public static final String ACTUALLY_ADDITIONS_MOD_ID = "actuallyadditions";

    private CompatibilityCheck() {
    }

    public enum Support { SUPPORTED, UNSUPPORTED, UNKNOWN }

    private static Support earlyResult;
    private static boolean loggedEarlyRefusal;
    private static Support enderUtilitiesResult;
    private static Support actuallyAdditionsResult;


    public static synchronized Support checkEarly() {
        if (earlyResult != null) {
            return earlyResult;
        }
        String ae2 = version(AE2_MOD_ID);
        String drawers = version(DRAWERS_MOD_ID);
        if (ae2 == null || drawers == null) {
            return Support.UNKNOWN;
        } else if (matches(ae2, OptimizationConfig.expectedAe2Version)
                && matches(drawers, OptimizationConfig.expectedStorageDrawersVersion)) {
            earlyResult = Support.SUPPORTED;
        } else {
            earlyResult = Support.UNSUPPORTED;
            if (!loggedEarlyRefusal) {
                loggedEarlyRefusal = true;
                Diagnostics.LOG.warn("Not applying any mixin: found {} {} and {} {}, and this build is "
                        + "written against {} and {}. AE2 keeps its own behavior.",
                        AE2_MOD_ID, ae2, DRAWERS_MOD_ID, drawers,
                        OptimizationConfig.expectedAe2Version, OptimizationConfig.expectedStorageDrawersVersion);
            }
        }
        return earlyResult;
    }

    public static void run() {
        String ae2 = version(AE2_MOD_ID);
        String drawers = version(DRAWERS_MOD_ID);

        Diagnostics.LOG.info("Found {} {} and {} {}", AE2_MOD_ID, describe(ae2), DRAWERS_MOD_ID, describe(drawers));

        boolean ae2Ok = matches(ae2, OptimizationConfig.expectedAe2Version);
        boolean drawersOk = matches(drawers, OptimizationConfig.expectedStorageDrawersVersion);
        if (ae2Ok && drawersOk) {
            return;
        }

        if (OptimizationConfig.allowUnverifiedModVersions) {
            Diagnostics.LOG.warn("Running the drawer optimization against unverified versions because "
                    + "allowUnverifiedModVersions is set. Expected {} {} and {} {}.",
                    AE2_MOD_ID, OptimizationConfig.expectedAe2Version,
                    DRAWERS_MOD_ID, OptimizationConfig.expectedStorageDrawersVersion);
            return;
        }

        OptimizationConfig.optimizeDrawerInventoryPolling = false;
        OptimizationConfig.optimizeDrawerNegativeExtraction = false;
        Diagnostics.LOG.warn("Drawer optimization disabled: this build is validated against {} {} and {} {}, "
                + "and those are not what is installed. AE2 keeps its own behavior. If the installed "
                + "versions are correct, put the strings logged above into expectedAe2Version and "
                + "expectedStorageDrawersVersion in {}, or set allowUnverifiedModVersions if you have "
                + "checked the behavior yourself.",
                AE2_MOD_ID, OptimizationConfig.expectedAe2Version,
                DRAWERS_MOD_ID, OptimizationConfig.expectedStorageDrawersVersion,
                OptimizationConfig.FILE_NAME);
    }

    public static synchronized Support checkEnderUtilities() {
        if (enderUtilitiesResult == null) {
            enderUtilitiesResult = checkOptionalMod(ENDER_UTILITIES_MOD_ID,
                    OptimizationConfig.expectedEnderUtilitiesVersion, "Ender Utilities");
        }
        return enderUtilitiesResult;
    }

    public static synchronized Support checkActuallyAdditions() {
        if (actuallyAdditionsResult == null) {
            actuallyAdditionsResult = checkOptionalMod(ACTUALLY_ADDITIONS_MOD_ID,
                    OptimizationConfig.expectedActuallyAdditionsVersion, "Actually Additions");
        }
        return actuallyAdditionsResult;
    }

    public static boolean isUsable(Support support) {
        if (support == Support.SUPPORTED) {
            return true;
        }
        return support == Support.UNSUPPORTED && OptimizationConfig.allowUnverifiedModVersions;
    }

    private static Support checkOptionalMod(String modId, String expectedVersion, String displayName) {
        String actual = version(modId);
        if (actual == null) {
            return Support.UNKNOWN;
        }
        if (matches(actual, expectedVersion)) {
            return Support.SUPPORTED;
        }
        if (OptimizationConfig.allowUnverifiedModVersions) {
            Diagnostics.LOG.warn("Applying the {} integration mixins against an unverified version: found "
                    + "{} {}, and this build is written against {}. Running because "
                    + "allowUnverifiedModVersions is set.",
                    displayName, modId, actual, expectedVersion);
        } else {
            Diagnostics.LOG.warn("Not applying the {} integration mixins: found {} {}, and this build is "
                    + "written against {}. That handler keeps its own behavior.",
                    displayName, modId, actual, expectedVersion);
        }
        return Support.UNSUPPORTED;
    }

    private static String version(String modId) {
        try {
            Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
            ModContainer container = mods == null ? null : mods.get(modId);
            return container == null ? null : container.getVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String describe(String version) {
        return version == null ? "(version unknown)" : version;
    }


    private static boolean matches(String actual, String expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        return actual != null && (actual.equals(expected) || actual.endsWith(expected));
    }
}
