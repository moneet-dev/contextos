package dev.moneet.contextos.eval;

import java.util.List;

/** A chat model: messages in, the assistant's text out. */
public interface ChatModel {

    record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    String complete(List<Message> messages);

    /** Model name, for reports. */
    String name();
}
