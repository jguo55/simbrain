package org.simbrain.workspace

import org.simbrain.util.PreferenceHolder
import org.simbrain.util.StringPreference
import org.simbrain.util.UserParameter
import org.simbrain.util.Utils

object WorkspacePreferences: PreferenceHolder() {

    @UserParameter(label = "Sim directory")
    var simulationDirectory by StringPreference("." + Utils.FS +"simulations" + Utils.FS + "workspaces");

}
