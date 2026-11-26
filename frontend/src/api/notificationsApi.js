import { request } from "./apiClient";

export async function getNotifications() {
  const res = await request("/api/notifications", { skipUnauthorizedHandler: true });
  return res || [];
}

export async function getUnreadCount() {
  const res = await request("/api/notifications/unread-count", { skipUnauthorizedHandler: true });
  return res || { count: 0 };
}

export async function markAsRead(id) {
  const res = await request(`/api/notifications/${id}/read`, {
    method: "PUT",
    skipUnauthorizedHandler: true
  });
  return res;
}
