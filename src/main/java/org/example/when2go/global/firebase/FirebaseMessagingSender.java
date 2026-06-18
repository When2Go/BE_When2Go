package org.example.when2go.global.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FirebaseMessagingSender implements FirebaseMessageSender {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public String send(Message message) throws FirebaseMessagingException {
        return firebaseMessaging.send(message);
    }
}
