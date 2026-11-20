#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>

@interface NotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@property(nonatomic) void (*openPage)(const char *);
@end

@implementation NotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))complete {
    complete(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList);
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))complete {
    NSString *page = response.notification.request.content.userInfo[@"page"];
    if ([response.actionIdentifier isEqualToString:UNNotificationDefaultActionIdentifier] &&
        [page isKindOfClass:NSString.class]) {
        self.openPage(page.UTF8String);
    }
    complete();
}
@end

void installNotificationHandler(void (*openPage)(const char *)) {
    @autoreleasepool {
        // The center holds its delegate weakly.
        static NotificationDelegate *delegate;
        delegate = [NotificationDelegate new];
        delegate.openPage = openPage;
        UNUserNotificationCenter.currentNotificationCenter.delegate = delegate;
    }
}

void postNotification(const char *message, const char *page) {
    @autoreleasepool {
        UNMutableNotificationContent *content = [UNMutableNotificationContent new];
        content.title = @"DevStruct";
        content.body = [NSString stringWithUTF8String:message];
        content.userInfo = @{@"page": [NSString stringWithUTF8String:page]};
        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:NSUUID.UUID.UUIDString
                                                                              content:content trigger:nil];
        UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
        [center requestAuthorizationWithOptions:UNAuthorizationOptionAlert completionHandler:^(BOOL granted, NSError *error) {
            if (error) NSLog(@"DevStruct notification authorization: %@", error);
            if (!granted) return;
            [center addNotificationRequest:request withCompletionHandler:^(NSError *postError) {
                if (postError) NSLog(@"DevStruct notification delivery: %@", postError);
            }];
        }];
    }
}
