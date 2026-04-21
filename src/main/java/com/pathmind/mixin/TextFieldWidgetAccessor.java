package com.pathmind.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {
    @Accessor("firstCharacterIndex")
    int pathmind$getFirstCharacterIndex();

    @Accessor("selectionStart")
    int pathmind$getSelectionStart();

    @Accessor("selectionEnd")
    int pathmind$getSelectionEnd();

    @Accessor("placeholder")
    Text pathmind$getPlaceholder();

    @Accessor("suggestion")
    String pathmind$getSuggestion();

    @Accessor("editableColor")
    int pathmind$getEditableColor();

    @Accessor("uneditableColor")
    int pathmind$getUneditableColor();

    @Accessor("editable")
    boolean pathmind$isEditable();

    @Accessor("textShadow")
    boolean pathmind$getTextShadow();
}
