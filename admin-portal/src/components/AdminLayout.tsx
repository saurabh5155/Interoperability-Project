import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Layout,
  Menu,
  Button,
  Typography,
  theme,
  Tooltip,
} from 'antd';
import {
  DashboardOutlined,
  ApartmentOutlined,
  SwapOutlined,
  FileTextOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { adminLogout } from '../api/authApi';

const { Sider, Header, Content } = Layout;

const NAV_ITEMS = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'EHR Management' },
  { key: '/routing-rules', icon: <SwapOutlined />, label: 'Routing Rules' },
  { key: '/transform-logs', icon: <FileTextOutlined />, label: 'Transform Logs' },
  { key: '/ehrs', icon: <ApartmentOutlined />, label: 'All EHRs' },
];

const AdminLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const { token } = theme.useToken();

  const handleLogout = () => {
    adminLogout();
    navigate('/login', { replace: true });
  };

  const selectedKey = NAV_ITEMS.find((item) =>
    location.pathname.startsWith(item.key)
  )?.key ?? '/dashboard';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        trigger={null}
        width={220}
        style={{ background: token.colorBgContainer, borderRight: `1px solid ${token.colorBorderSecondary}` }}
      >
        <div style={{ padding: '16px', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
          {!collapsed && (
            <Typography.Text strong style={{ fontSize: 14 }}>
              Interop Admin
            </Typography.Text>
          )}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 'none', marginTop: 8 }}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            padding: '0 16px',
            background: token.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Tooltip title="Sign out">
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={handleLogout}
              danger
            >
              Sign Out
            </Button>
          </Tooltip>
        </Header>

        <Content style={{ margin: 24, minHeight: 360 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default AdminLayout;
