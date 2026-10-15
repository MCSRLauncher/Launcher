package com.redlimerl.mcsrlauncher.gui.component

import java.io.File
import javax.swing.tree.DefaultMutableTreeNode

class CheckBoxTreeNode(val file: File, var selected: Boolean = true) : DefaultMutableTreeNode(file.name)
