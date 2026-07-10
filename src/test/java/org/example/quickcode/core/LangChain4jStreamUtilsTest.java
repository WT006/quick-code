package org.example.quickcode.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jStreamUtilsTest {

    @Test
    void isToolLimitExceededError_matchesLangChain4jMessage() {
        RuntimeException error = new RuntimeException(
                "Something is wrong, exceeded 5 sequential tool invocations");
        assertTrue(LangChain4jStreamUtils.isToolLimitExceededError(error));
    }

    @Test
    void isToolLimitExceededError_matchesWrappedCause() {
        RuntimeException wrapped = new RuntimeException("outer",
                new RuntimeException("exceeded 5 sequential tool invocations"));
        assertTrue(LangChain4jStreamUtils.isToolLimitExceededError(wrapped));
    }

    @Test
    void isToolLimitExceededError_returnsFalseForOtherErrors() {
        assertFalse(LangChain4jStreamUtils.isToolLimitExceededError(
                new RuntimeException("network timeout")));
    }
}
