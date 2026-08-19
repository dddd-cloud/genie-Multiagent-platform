import { memo, useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { AdminUserResponse, UserRole, UserStatus } from '@/contracts';
import { USER_ROLES, USER_STATUSES } from '@/contracts';
import { MvpApiError } from '@/services/apiError';
import {
  createAdminUser,
  listAdminUsers,
  resetAdminUserPassword,
  updateAdminUserStatus,
} from '@/services/admin';

const PAGE_SIZE = 20;
const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 64;

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: '管理员',
  USER: '普通用户',
};

const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

type CreateForm = {
  username: string;
  displayName: string;
  password: string;
  role: UserRole;
};

const EMPTY_CREATE_FORM: CreateForm = {
  username: '',
  displayName: '',
  password: '',
  role: 'USER',
};

function errorText(err: unknown, fallback: string): string {
  if (err instanceof MvpApiError) {
    if (err.code === 'USER_ALREADY_EXISTS') {
      return '这个用户名已经存在';
    }
    if (err.code === 'VALIDATION_ERROR') {
      return '填写内容不符合要求，请检查后重试';
    }
    if (err.code === 'RESOURCE_NOT_FOUND') {
      return '用户不存在或已被删除';
    }
    if (err.code === 'ACCESS_DENIED') {
      return '当前账户没有管理员权限';
    }
    return err.message;
  }
  return fallback;
}

const AdminUsersPage: GenieType.FC = memo(() => {
  const [items, setItems] = useState<AdminUserResponse[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState<UserRole | undefined>(undefined);
  const [status, setStatus] = useState<UserStatus | undefined>(undefined);
  const [createForm, setCreateForm] = useState<CreateForm | null>(null);
  const [creating, setCreating] = useState(false);
  const [resetTarget, setResetTarget] = useState<AdminUserResponse | null>(null);
  const [resetPassword, setResetPassword] = useState('');
  const [resetting, setResetting] = useState(false);
  const [pendingStatusId, setPendingStatusId] = useState<string | null>(null);

  const load = useCallback(
    async (nextPage: number, signal?: AbortSignal) => {
      setLoading(true);
      setError(null);
      try {
        const data = await listAdminUsers(
          {
            page: nextPage,
            pageSize: PAGE_SIZE,
            keyword,
            role,
            status,
          },
          signal,
        );
        if (signal?.aborted) {
          return;
        }
        setItems(data?.items ?? []);
        setPage(data?.page ?? nextPage);
        setHasMore(data?.hasMore ?? false);
      } catch (err: unknown) {
        if (signal?.aborted) {
          return;
        }
        setError(errorText(err, '加载用户列表失败'));
      } finally {
        if (!signal?.aborted) {
          setLoading(false);
        }
      }
    },
    [keyword, role, status],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(1, controller.signal);
    return () => controller.abort();
  }, [load]);

  const submitCreate = async () => {
    if (!createForm) {
      return;
    }
    const username = createForm.username.trim();
    const displayName = createForm.displayName.trim();
    if (!username || !displayName) {
      message.warning('请填写用户名和显示名');
      return;
    }
    if (
      createForm.password.length < MIN_PASSWORD_LENGTH ||
      createForm.password.length > MAX_PASSWORD_LENGTH
    ) {
      message.warning(`密码需要 ${MIN_PASSWORD_LENGTH}-${MAX_PASSWORD_LENGTH} 位`);
      return;
    }
    setCreating(true);
    try {
      await createAdminUser({
        username,
        displayName,
        password: createForm.password,
        role: createForm.role,
      });
      message.success('已创建用户');
      setCreateForm(null);
      await load(1);
    } catch (err: unknown) {
      message.error(errorText(err, '创建用户失败'));
    } finally {
      setCreating(false);
    }
  };

  const toggleStatus = async (user: AdminUserResponse) => {
    const next: UserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    setPendingStatusId(user.id);
    try {
      await updateAdminUserStatus(user.id, next);
      message.success(next === 'DISABLED' ? '已停用，该用户会被强制下线' : '已启用');
      await load(page);
    } catch (err: unknown) {
      message.error(errorText(err, '修改状态失败'));
    } finally {
      setPendingStatusId(null);
    }
  };

  const submitReset = async () => {
    if (!resetTarget) {
      return;
    }
    if (
      resetPassword.length < MIN_PASSWORD_LENGTH ||
      resetPassword.length > MAX_PASSWORD_LENGTH
    ) {
      message.warning(`密码需要 ${MIN_PASSWORD_LENGTH}-${MAX_PASSWORD_LENGTH} 位`);
      return;
    }
    setResetting(true);
    try {
      await resetAdminUserPassword(resetTarget.id, resetPassword);
      message.success('已重置密码，该用户的登录会话已失效');
      setResetTarget(null);
      setResetPassword('');
    } catch (err: unknown) {
      message.error(errorText(err, '重置密码失败'));
    } finally {
      setResetting(false);
    }
  };

  const columns: ColumnsType<AdminUserResponse> = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (value: string) => (
        <span className="text-text-primary">{value}</span>
      ),
    },
    {
      title: '显示名',
      dataIndex: 'displayName',
      key: 'displayName',
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      render: (value: UserRole) => ROLE_LABELS[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (value: UserStatus) => (
        <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>
          {STATUS_LABELS[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (value: string) => (
        <span className="text-[13px] text-text-secondary">
          {value ? value.slice(0, 19).replace('T', ' ') : '—'}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: AdminUserResponse) => (
        <Space size={8}>
          <Button
            size="small"
            loading={pendingStatusId === record.id}
            onClick={() => void toggleStatus(record)}
          >
            {record.status === 'ACTIVE' ? '停用' : '启用'}
          </Button>
          <Button
            size="small"
            onClick={() => {
              setResetTarget(record);
              setResetPassword('');
            }}
          >
            重置密码
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="h-full overflow-auto bg-page">
      <div className="mx-auto flex max-w-[1080px] flex-col gap-20 px-24 py-36">
        <header>
          <h1 className="m-0 text-[28px] font-semibold tracking-[-0.02em] text-text-primary">
            用户管理
          </h1>
          <p className="mt-8 mb-0 text-[15px] leading-[22px] text-text-secondary">
            停用账户或重置密码后，该用户已登录的会话会立即失效。
          </p>
        </header>

        <div className="flex flex-wrap items-center gap-8">
          <Input.Search
            className="w-[240px]"
            placeholder="搜索用户名或显示名"
            allowClear
            maxLength={64}
            onSearch={(value) => setKeyword(value)}
          />
          <Select<UserRole>
            className="w-[140px]"
            placeholder="全部角色"
            allowClear
            value={role}
            options={USER_ROLES.map((item) => ({
              value: item,
              label: ROLE_LABELS[item],
            }))}
            onChange={(value) => setRole(value)}
          />
          <Select<UserStatus>
            className="w-[140px]"
            placeholder="全部状态"
            allowClear
            value={status}
            options={USER_STATUSES.map((item) => ({
              value: item,
              label: STATUS_LABELS[item],
            }))}
            onChange={(value) => setStatus(value)}
          />
          <div className="flex-1" />
          <Button
            type="primary"
            onClick={() => setCreateForm({ ...EMPTY_CREATE_FORM })}
          >
            新建用户
          </Button>
        </div>

        {error ? (
          <Alert
            type="warning"
            showIcon
            message={error}
            action={
              <Button size="small" onClick={() => void load(page)}>
                重试
              </Button>
            }
          />
        ) : null}

        <Table<AdminUserResponse>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={items}
          pagination={false}
          locale={{ emptyText: '没有符合条件的用户' }}
        />

        <div className="flex items-center justify-between">
          <Button
            size="small"
            disabled={page <= 1 || loading}
            onClick={() => void load(page - 1)}
          >
            上一页
          </Button>
          <span className="text-[13px] text-text-tertiary">第 {page} 页</span>
          <Button
            size="small"
            disabled={!hasMore || loading}
            onClick={() => void load(page + 1)}
          >
            下一页
          </Button>
        </div>
      </div>

      <Modal
        title="新建用户"
        open={createForm != null}
        okText="创建"
        cancelText="取消"
        confirmLoading={creating}
        onOk={() => void submitCreate()}
        onCancel={() => setCreateForm(null)}
        destroyOnHidden
      >
        <div className="flex flex-col gap-12 pt-8">
          <Input
            placeholder="用户名（登录用）"
            value={createForm?.username ?? ''}
            maxLength={64}
            onChange={(event) =>
              setCreateForm((current) =>
                current ? {
                  ...current,
                  username: event.target.value
                } : current,
              )
            }
          />
          <Input
            placeholder="显示名"
            value={createForm?.displayName ?? ''}
            maxLength={64}
            onChange={(event) =>
              setCreateForm((current) =>
                current
                  ? {
                    ...current,
                    displayName: event.target.value
                  }
                  : current,
              )
            }
          />
          <Input.Password
            placeholder={`初始密码（${MIN_PASSWORD_LENGTH}-${MAX_PASSWORD_LENGTH} 位）`}
            value={createForm?.password ?? ''}
            maxLength={MAX_PASSWORD_LENGTH}
            onChange={(event) =>
              setCreateForm((current) =>
                current ? {
                  ...current,
                  password: event.target.value
                } : current,
              )
            }
          />
          <Select<UserRole>
            value={createForm?.role ?? 'USER'}
            options={USER_ROLES.map((item) => ({
              value: item,
              label: ROLE_LABELS[item],
            }))}
            onChange={(value) =>
              setCreateForm((current) =>
                current ? {
                  ...current,
                  role: value
                } : current,
              )
            }
          />
        </div>
      </Modal>

      <Modal
        title={`重置 ${resetTarget?.username ?? ''} 的密码`}
        open={resetTarget != null}
        okText="重置"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={resetting}
        onOk={() => void submitReset()}
        onCancel={() => setResetTarget(null)}
        destroyOnHidden
      >
        <p className="mt-0 mb-12 text-[14px] leading-[22px] text-text-secondary">
          重置后该用户会被强制下线，需要用新密码重新登录。
        </p>
        <Input.Password
          placeholder={`新密码（${MIN_PASSWORD_LENGTH}-${MAX_PASSWORD_LENGTH} 位）`}
          value={resetPassword}
          maxLength={MAX_PASSWORD_LENGTH}
          onChange={(event) => setResetPassword(event.target.value)}
        />
      </Modal>
    </div>
  );
});

AdminUsersPage.displayName = 'AdminUsersPage';

export default AdminUsersPage;
