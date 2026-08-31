package com.pathmind.nodes;

import java.util.ArrayList;
import java.util.List;

final class NodeTextContent {
    interface Host {
        void onTextContentLayoutChanged();
    }

    private final NodeType type;
    private final Host host;
    private final List<String> messageLines;
    private boolean messageClientSide;
    private String bookText;
    private final List<String> bookPages;

    NodeTextContent(NodeType type, Host host) {
        this.type = type;
        this.host = host;
        this.messageLines = new ArrayList<>();
        if (type == NodeType.MESSAGE || type == NodeType.CALCULATE || type == NodeType.ALERT) {
            this.messageLines.add(getDefaultMessageLineValue());
        }
        this.messageClientSide = false;
        this.bookText = "";
        this.bookPages = new ArrayList<>();
    }

    int getMessageFieldCount() {
        return Math.max(1, messageLines.size());
    }

    List<String> getMessageLines() {
        return messageLines;
    }

    String getMessageLine(int index) {
        if (index < 0 || index >= getMessageFieldCount()) {
            return "";
        }
        if (index >= messageLines.size()) {
            return "";
        }
        String value = messageLines.get(index);
        return value == null ? "" : value;
    }

    void setMessageLine(int index, String value) {
        if (!hasMessageInputFields() || index < 0 || index >= Node.MAX_MESSAGE_LINES) {
            return;
        }
        while (index >= messageLines.size()) {
            messageLines.add(getDefaultMessageLineValue());
        }
        messageLines.set(index, sanitizeMessageLine(value));
    }

    void setMessageLines(List<String> lines) {
        messageLines.clear();
        if (lines != null) {
            for (String line : lines) {
                if (messageLines.size() >= Node.MAX_MESSAGE_LINES) {
                    break;
                }
                messageLines.add(sanitizeMessageLine(line));
            }
        }
        if (messageLines.isEmpty()) {
            messageLines.add(getDefaultMessageLineValue());
        }
        host.onTextContentLayoutChanged();
    }

    void addMessageLine(String value) {
        if (!hasMessageInputFields()) {
            return;
        }
        if (messageLines.size() >= Node.MAX_MESSAGE_LINES) {
            return;
        }
        String lineValue = sanitizeMessageLine(value);
        if (type == NodeType.CALCULATE && lineValue.isBlank()) {
            lineValue = getDefaultCalculationLineValue(messageLines.size());
        }
        messageLines.add(lineValue);
        host.onTextContentLayoutChanged();
    }

    boolean removeMessageLine(int index) {
        if (!hasMessageInputFields() || messageLines.size() <= 1) {
            return false;
        }
        if (index < 0 || index >= messageLines.size()) {
            return false;
        }
        messageLines.remove(index);
        host.onTextContentLayoutChanged();
        return true;
    }

    boolean isMessageClientSide() {
        return type == NodeType.MESSAGE && messageClientSide;
    }

    boolean hasMessageScopeToggle() {
        return type == NodeType.MESSAGE;
    }

    void setMessageClientSide(boolean messageClientSide) {
        if (type != NodeType.MESSAGE) {
            return;
        }
        this.messageClientSide = messageClientSide;
    }

    void toggleMessageClientSide() {
        if (type != NodeType.MESSAGE) {
            return;
        }
        messageClientSide = !messageClientSide;
    }

    String getMessageFieldLabelText(int index) {
        if (type == NodeType.CALCULATE) {
            return "Output " + getCalculationVariableLabel(index);
        }
        return getMessageFieldCount() > 1 ? "Message " + (index + 1) : "Message";
    }

    boolean hasMessageInputFields() {
        return type == NodeType.MESSAGE || type == NodeType.CALCULATE || type == NodeType.ALERT;
    }

    boolean hasBookTextInput() {
        return type == NodeType.WRITE_BOOK || type == NodeType.WRITE_SIGN;
    }

    boolean hasBookTextPageInput() {
        return type == NodeType.WRITE_BOOK;
    }

    String getBookText() {
        return getBookTextForPage(1);
    }

    void setBookText(String text) {
        setBookTextForPage(1, text);
    }

    int getBookTextMaxChars() {
        return type == NodeType.WRITE_SIGN ? Node.SIGN_MAX_CHARS : Node.BOOK_PAGE_MAX_CHARS;
    }

    int getBookTextMaxCharsPerLine() {
        return type == NodeType.WRITE_SIGN ? Node.SIGN_LINE_MAX_CHARS : 0;
    }

    int getBookTextMaxLines() {
        return type == NodeType.WRITE_SIGN ? Node.SIGN_MAX_LINES : 0;
    }

    int getBookTextPopupWidth() {
        return type == NodeType.WRITE_SIGN ? 300 : 340;
    }

    int getBookTextPopupHeight() {
        return type == NodeType.WRITE_SIGN ? 230 : 280;
    }

    String getBookTextEditorTitle() {
        return type == NodeType.WRITE_SIGN ? "Edit Sign Text" : "Edit Book Text";
    }

    String getBookTextForPage(int pageNumber) {
        if (type == NodeType.WRITE_SIGN) {
            return bookText != null ? bookText : "";
        }
        int pageIndex = Math.max(0, pageNumber - 1);
        if (pageIndex < bookPages.size()) {
            String value = bookPages.get(pageIndex);
            return value != null ? value : "";
        }
        if (pageIndex == 0 && bookText != null) {
            return bookText;
        }
        return "";
    }

    void setBookTextForPage(int pageNumber, String text) {
        if (type == NodeType.WRITE_SIGN) {
            bookText = normalizeSignText(text);
            return;
        }
        int safePageNumber = Math.max(1, pageNumber);
        ensureBookPageCapacity(safePageNumber);
        String normalized = text == null ? "" : text;
        if (normalized.length() > Node.BOOK_PAGE_MAX_CHARS) {
            normalized = normalized.substring(0, Node.BOOK_PAGE_MAX_CHARS);
        }
        bookPages.set(safePageNumber - 1, normalized);
        if (safePageNumber == 1) {
            bookText = normalized;
        }
    }

    List<String> getBookPages() {
        return new ArrayList<>(bookPages);
    }

    void setBookPages(List<String> pages) {
        if (type == NodeType.WRITE_SIGN) {
            String first = (pages == null || pages.isEmpty()) ? "" : pages.get(0);
            bookText = normalizeSignText(first);
            return;
        }
        bookPages.clear();
        if (pages != null) {
            for (String page : pages) {
                String normalized = page == null ? "" : page;
                if (normalized.length() > Node.BOOK_PAGE_MAX_CHARS) {
                    normalized = normalized.substring(0, Node.BOOK_PAGE_MAX_CHARS);
                }
                bookPages.add(normalized);
            }
        }
        if (bookPages.isEmpty()) {
            bookPages.add("");
        }
        bookText = bookPages.getFirst();
    }

    private String getDefaultMessageLineValue() {
        return type == NodeType.CALCULATE ? getDefaultCalculationLineValue(messageLines.size()) : "Hello World";
    }

    private String sanitizeMessageLine(String value) {
        String sanitized = value == null ? "" : value;
        if (sanitized.length() > Node.MAX_MESSAGE_LINE_LENGTH) {
            return sanitized.substring(0, Node.MAX_MESSAGE_LINE_LENGTH);
        }
        return sanitized;
    }

    private String getDefaultCalculationLineValue(int index) {
        return getCalculationVariableLabel(index) + " = 0";
    }

    private String getCalculationVariableLabel(int index) {
        int value = Math.max(0, index);
        StringBuilder builder = new StringBuilder();
        do {
            int remainder = value % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = value / 26 - 1;
        } while (value >= 0);
        return builder.toString();
    }

    private void ensureBookPageCapacity(int pageNumber) {
        int targetSize = Math.max(1, pageNumber);
        while (bookPages.size() < targetSize) {
            bookPages.add("");
        }
    }

    private String normalizeSignText(String raw) {
        String text = raw == null ? "" : raw;
        if (text.length() > Node.SIGN_MAX_CHARS) {
            text = text.substring(0, Node.SIGN_MAX_CHARS);
        }
        String[] split = text.split("\\n", -1);
        int lineCount = Math.min(Node.SIGN_MAX_LINES, split.length);
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            String line = split[i] == null ? "" : split[i];
            if (line.length() > Node.SIGN_LINE_MAX_CHARS) {
                line = line.substring(0, Node.SIGN_LINE_MAX_CHARS);
            }
            if (i > 0) {
                normalized.append('\n');
            }
            normalized.append(line);
        }
        String result = normalized.toString();
        if (result.length() > Node.SIGN_MAX_CHARS) {
            return result.substring(0, Node.SIGN_MAX_CHARS);
        }
        return result;
    }
}
