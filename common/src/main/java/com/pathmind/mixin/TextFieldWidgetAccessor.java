package com.pathmind.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface TextFieldWidgetAccessor {
    @Accessor("displayPos")
    int pathmind$getFirstCharacterIndex();

    @Accessor("cursorPos")
    int pathmind$getSelectionStart();

    @Accessor("highlightPos")
    int pathmind$getSelectionEnd();

    @Accessor("hint")
    Component pathmind$getPlaceholder();

    @Accessor("suggestion")
    String pathmind$getSuggestion();

    @Accessor("textColor")
    int pathmind$getEditableColor();

    @Accessor("textColorUneditable")
    int pathmind$getUneditableColor();

    @Accessor("isEditable")
    boolean pathmind$isEditable();
}
