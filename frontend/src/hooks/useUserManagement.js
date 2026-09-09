import { useState, useEffect, useCallback } from "react";
import { apiError, apiFetch } from "../api";

export default function useUserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Filters
  const [keyword, setKeyword] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const size = 5;

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const query = new URLSearchParams({
        page: page,
        size: size,
        ...(keyword && { keyword }),
        ...(jobTitle && { jobTitle }),
        ...(role && { role }),
      });

      const res = await apiFetch(`/api/users?${query.toString()}`, {
        headers: {
          "Content-Type": "application/json",
        },
      });
      if (!res.ok) throw new Error("Failed to fetch users");
      const data = await res.json();
      setUsers(data.content || []);
      setTotalElements(data.totalElements || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [keyword, jobTitle, role, page, size]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const createUser = async (userData) => {
    const res = await apiFetch("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: userData.email?.trim(),
        password: userData.password,
        displayName: userData.displayName?.trim(),
        jobTitle: userData.jobTitle?.trim() || null,
        managerId: userData.managerId || null,
        systemRoles: userData.systemRoles || [],
      }),
    });
    if (!res.ok) throw await userRequestError(res, "Không thể tạo User");
    const created = await res.json();
    await fetchUsers();
    return created;
  };

  const updateUser = async (id, userData) => {
    const res = await apiFetch(`/api/users/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        displayName: userData.displayName?.trim(),
        jobTitle: userData.jobTitle?.trim() || null,
        managerId: userData.managerId || null,
        systemRoles: userData.systemRoles || [],
        newPassword: userData.newPassword || null,
      }),
    });
    if (!res.ok) throw await userRequestError(res, "Không thể cập nhật User");
    const updated = await res.json();
    await fetchUsers();
    return updated;
  };

  const deactivateUser = async (id) => {
    const res = await apiFetch(`/api/users/${id}/deactivate`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
    });
    if (!res.ok) throw new Error("Failed to deactivate user");
    fetchUsers();
  };

  const fetchDropdownUsers = async (excludeId) => {
    const url = excludeId
      ? `/api/users/dropdown?exclude=${excludeId}`
      : "/api/users/dropdown";
    const res = await apiFetch(url);
    if (!res.ok) throw new Error("Failed to fetch manager dropdown");
    return await res.json();
  };

  const fetchUserById = async (id) => {
    const res = await apiFetch(`/api/users/${id}`);
    if (!res.ok) throw new Error(await apiError(res, "Không thể tải thông tin User"));
    return await res.json();
  };

  return {
    users,
    loading,
    error,
    totalElements,
    totalPages,
    page,
    setPage,
    keyword,
    setKeyword,
    jobTitle,
    setJobTitle,
    role,
    setRole,
    fetchUsers,
    createUser,
    updateUser,
    deactivateUser,
    fetchUserById,
    fetchDropdownUsers,
  };
}

async function userRequestError(response, fallback) {
  const body = await response
    .clone()
    .json()
    .catch(() => ({}));
  const error = new Error(await apiError(response, fallback));
  error.fieldErrors = body.fieldErrors || {};
  return error;
}
