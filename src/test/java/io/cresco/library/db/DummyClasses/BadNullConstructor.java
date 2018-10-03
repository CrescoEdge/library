package io.cresco.library.db.DummyClasses;

import java.nio.charset.CharacterCodingException;

public class BadNullConstructor {
    public BadNullConstructor() throws CharacterCodingException {
        throw new CharacterCodingException();
    }
}
