package com.example.dbids.modules.proxy;

import java.nio.ByteBuffer;
import java.nio.charset.*;

public class Utf8Validator {
    private static final CharsetDecoder DECODER = StandardCharsets.UTF_8
            .newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    public static void ensureUtf8(byte[] bytes) {
        try { DECODER.decode(ByteBuffer.wrap(bytes)); }
        catch (CharacterCodingException e) {
            throw new IllegalArgumentException("invalid UTF-8");
        }
    }
}
