package org.example.when2go.global.firebase;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

public interface FirebaseMessageSender {

    String send(Message message) throws FirebaseMessagingException;
}
