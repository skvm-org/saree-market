package com.sareemarket.app.data

import retrofit2.Response

fun followItem(id: Long, name: String = "Shop $id", mode: NotifyMode = NotifyMode.INSTANT) = FollowItem(id, name, "Erode", mode)

class FakeFollowsApi : FollowsApi {
    var list: () -> List<FollowItem> = { emptyList() }
    var onFollow: (Long, NotifyMode) -> Response<Unit> = { _, _ -> Response.success(Unit) }
    var onUnfollow: (Long) -> Response<Unit> = { Response.success(Unit) }
    val calls = mutableListOf<String>()

    override suspend fun follows() = list()

    override suspend fun follow(sellerId: Long, body: FollowBody): Response<Unit> {
        calls += "follow $sellerId ${body.notifyMode}"
        return onFollow(sellerId, body.notifyMode)
    }

    override suspend fun unfollow(sellerId: Long): Response<Unit> {
        calls += "unfollow $sellerId"
        return onUnfollow(sellerId)
    }
}

fun note(
    id: Long,
    read: Boolean = false,
    type: NotificationType = NotificationType.NEW_COLLECTION,
    updatedAt: String = "2026-10-25T10:00:00Z",
    message: String = "Lakshmi Silks added 3 sarees · Diwali silks",
) = AppNotification(id, type, 3, 1, "Lakshmi Silks", 5, "Diwali silks", message, read, updatedAt, updatedAt)

fun notePage(page: Int, totalPages: Int, perPage: Int = 2) = NotificationPage(
    items = (0 until perPage).map { note(page * 100L + it) },
    page = page, pageSize = perPage, totalItems = totalPages.toLong() * perPage, totalPages = totalPages,
)

class FakeNotificationsApi : NotificationsApi {
    var onPage: (Int) -> NotificationPage = { notePage(it, totalPages = 2) }
    var unread = 3L
    var onMarkRead: (Long) -> Response<Unit> = { Response.success(Unit) }
    var onMarkAll: () -> Response<Unit> = { Response.success(Unit) }
    val calls = mutableListOf<String>()

    override suspend fun notifications(page: Int): NotificationPage {
        calls += "page $page"
        return onPage(page)
    }

    override suspend fun unreadCount() = UnreadCount(unread)

    override suspend fun markRead(id: Long): Response<Unit> {
        calls += "read $id"
        return onMarkRead(id).also { if (it.isSuccessful && unread > 0) unread-- }
    }

    override suspend fun markAllRead(): Response<Unit> {
        calls += "read-all"
        return onMarkAll().also { if (it.isSuccessful) unread = 0 }
    }
}

fun errorResponse(code: Int, body: String = """{"code":"X","message":"Nope"}"""): Response<Unit> =
    Response.error(code, okhttp3.ResponseBody.create(null, body))
