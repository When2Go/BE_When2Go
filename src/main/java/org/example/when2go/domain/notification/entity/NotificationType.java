package org.example.when2go.domain.notification.entity;

import org.example.when2go.domain.notification.dto.NotificationMessage;

public enum NotificationType {
    DEPART_10MIN {
        @Override
        public NotificationMessage toMessage() {
            return new NotificationMessage(
                    "출발 10분 전이에요",
                    "이제 출발 준비를 시작하세요."
            );
        }
    },
    DEPART_NOW {
        @Override
        public NotificationMessage toMessage() {
            return new NotificationMessage(
                    "지금 출발할 시간이에요",
                    "예정된 도착 시간에 맞추려면 지금 출발하세요."
            );
        }
    },
    IMMEDIATE_LATE {
        @Override
        public NotificationMessage toMessage() {
            return new NotificationMessage(
                    "출발 시간이 지났어요",
                    "도착 예정 시간을 다시 확인해 주세요."
            );
        }
    },
    AD_CONTEXT {
        @Override
        public NotificationMessage toMessage() {
            return new NotificationMessage(
                    "이동 전 확인해 보세요",
                    "출발 전 필요한 정보를 확인해 주세요."
            );
        }
    };

    public abstract NotificationMessage toMessage();
}
