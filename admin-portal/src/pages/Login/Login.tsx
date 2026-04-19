import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Form, Input, message, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { adminLogin } from '../../api/authApi';

const { Title } = Typography;

interface LoginFormValues {
  username: string;
  password: string;
}

const Login: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      await adminLogin(values.username, values.password);
      message.success('Login successful');
      navigate('/dashboard', { replace: true });
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) {
        message.error('Invalid username or password');
      } else if (status === 429) {
        message.error('Too many login attempts. Please wait before retrying.');
      } else {
        message.error('Login failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <Card style={styles.card}>
        <div style={styles.header}>
          <Title level={3} style={{ margin: 0 }}>
            Healthcare Interop Admin
          </Title>
          <Typography.Text type="secondary">
            Sign in to the administration portal
          </Typography.Text>
        </div>

        <Form<LoginFormValues>
          layout="vertical"
          onFinish={onFinish}
          autoComplete="off"
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: 'Username is required' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="Username" />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Password is required' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="Password" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={loading} block>
              Sign In
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#f0f2f5',
  },
  card: {
    width: 400,
    borderRadius: 8,
    boxShadow: '0 4px 24px rgba(0,0,0,0.10)',
  },
  header: {
    textAlign: 'center',
    marginBottom: 32,
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
  },
};

export default Login;
