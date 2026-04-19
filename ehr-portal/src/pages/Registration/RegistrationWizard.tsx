import React, { useState } from 'react';
import {
  Steps, Card, Form, Input, Select, Button, Space, Alert,
  Divider, Typography, message, Result, Tag, Tooltip
} from 'antd';
import {
  UserOutlined, ApiOutlined, SettingOutlined,
  CheckCircleOutlined, LinkOutlined, CopyOutlined, EyeOutlined, EyeInvisibleOutlined
} from '@ant-design/icons';
import axios from 'axios';

const { Step } = Steps;
const { Option } = Select;
const { Title, Text, Paragraph } = Typography;

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

interface StepOneData {
  ehrCode: string;
  displayName: string;
  orgName: string;
  contactEmail: string;
  version: string;
  description: string;
}

interface StepTwoData {
  baseUrl: string;
  authType: string;
  authConfig: Record<string, string>;
}

interface EndpointConfig {
  operationType: string;
  httpMethod: string;
  pathTemplate: string;
  payloadTemplate?: string;
  responsePath?: string;
  timeoutMs: number;
}

export const RegistrationWizard: React.FC = () => {
  const [current, setCurrent] = useState(0);
  const [stepOne, setStepOne] = useState<StepOneData>({} as StepOneData);
  const [stepTwo, setStepTwo] = useState<StepTwoData>({} as StepTwoData);
  const [endpoints, setEndpoints] = useState<EndpointConfig[]>([]);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [apiKey, setApiKey] = useState<string>('');
  const [apiKeyVisible, setApiKeyVisible] = useState(false);
  const [loading, setLoading] = useState(false);

  const [form1] = Form.useForm();
  const [form2] = Form.useForm();

  const AUTH_FIELDS: Record<string, Array<{ name: string; label: string; secret?: boolean }>> = {
    BEARER_TOKEN: [{ name: 'token', label: 'Bearer Token', secret: true }],
    API_KEY: [
      { name: 'headerName', label: 'Header Name (e.g., X-API-Key)' },
      { name: 'apiKey', label: 'API Key Value', secret: true },
    ],
    OAUTH2_CLIENT_CREDENTIALS: [
      { name: 'tokenUrl', label: 'Token URL' },
      { name: 'clientId', label: 'Client ID' },
      { name: 'clientSecret', label: 'Client Secret', secret: true },
      { name: 'scope', label: 'Scope (optional)' },
    ],
    BASIC_AUTH: [
      { name: 'username', label: 'Username' },
      { name: 'password', label: 'Password', secret: true },
    ],
  };

  const addEndpoint = () => {
    setEndpoints([...endpoints, {
      operationType: 'SAVE_PATIENT',
      httpMethod: 'POST',
      pathTemplate: '/patients',
      timeoutMs: 30000,
    }]);
  };

  const updateEndpoint = (index: number, field: keyof EndpointConfig, value: any) => {
    const updated = [...endpoints];
    (updated[index] as any)[field] = value;
    setEndpoints(updated);
  };

  const removeEndpoint = (index: number) => {
    setEndpoints(endpoints.filter((_, i) => i !== index));
  };

  const testConnection = async () => {
    setLoading(true);
    try {
      const r = await axios.post(`${API_BASE}/api/v1/ehr/${stepOne.ehrCode}/test`);
      setTestResult({ success: r.data.connectionSuccessful, message: r.data.message });
    } catch {
      setTestResult({ success: false, message: 'Connection failed - check URL and credentials' });
    } finally {
      setLoading(false);
    }
  };

  const submit = async () => {
    setLoading(true);
    try {
      const authConfig: Record<string, string> = {};
      (AUTH_FIELDS[stepTwo.authType] || []).forEach(f => {
        authConfig[f.name] = (stepTwo.authConfig || {})[f.name] || '';
      });

      const regResp = await axios.post(`${API_BASE}/api/v1/ehr/register`, {
        ...stepOne,
        ...stepTwo,
        authConfig,
      });

      for (const ep of endpoints) {
        await axios.post(`${API_BASE}/api/v1/ehr/${stepOne.ehrCode}/endpoints`, ep);
      }

      setApiKey(regResp.data.apiKey);
      setCurrent(5);
    } catch (e: any) {
      message.error(e.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  const copyApiKey = () => {
    if (!apiKey) return;
    navigator.clipboard.writeText(apiKey).then(() => {
      message.success('API key copied to clipboard');
    }).catch(() => {
      message.error('Copy failed — please select and copy the key manually');
    });
  };

  const maskedKey = apiKey
    ? apiKey.substring(0, 8) + '•'.repeat(Math.max(0, apiKey.length - 12)) + apiKey.slice(-4)
    : '';

  const steps = [
    {
      title: 'Identity',
      icon: <UserOutlined />,
      content: (
        <Form form={form1} layout="vertical" onFinish={(v) => { setStepOne(v); setCurrent(1); }}>
          <Form.Item name="ehrCode" label="EHR System Code" rules={[{ required: true }]}
            help="Unique code (e.g., ECW, MYCLINIC) — uppercase, no spaces">
            <Input placeholder="e.g., MYCLINIC_001" style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item name="displayName" label="Display Name" rules={[{ required: true }]}>
            <Input placeholder="e.g., My Clinic EHR System" />
          </Form.Item>
          <Form.Item name="orgName" label="Organization Name">
            <Input placeholder="e.g., My Healthcare Organization" />
          </Form.Item>
          <Form.Item name="contactEmail" label="Contact Email" rules={[{ type: 'email' }]}>
            <Input placeholder="admin@myclinic.com" />
          </Form.Item>
          <Form.Item name="version" label="EHR Version">
            <Input placeholder="e.g., 2.1.0" />
          </Form.Item>
          <Button type="primary" htmlType="submit">Next</Button>
        </Form>
      ),
    },
    {
      title: 'Connection',
      icon: <LinkOutlined />,
      content: (
        <Form form={form2} layout="vertical"
          onFinish={(v) => { setStepTwo(v); setCurrent(2); }}>
          <Form.Item name="baseUrl" label="Base API URL" rules={[{ required: true }]}
            help="The root URL of your EHR API (without trailing slash)">
            <Input placeholder="https://api.myclinic.com/v1" />
          </Form.Item>
          <Form.Item name="authType" label="Authentication Type" rules={[{ required: true }]}>
            <Select placeholder="Select auth type">
              <Option value="NONE">None (no authentication)</Option>
              <Option value="BEARER_TOKEN">Bearer Token</Option>
              <Option value="API_KEY">API Key Header</Option>
              <Option value="OAUTH2_CLIENT_CREDENTIALS">OAuth2 Client Credentials</Option>
              <Option value="BASIC_AUTH">Basic Auth (username/password)</Option>
            </Select>
          </Form.Item>
          <Form.Item shouldUpdate={(prev, cur) => prev.authType !== cur.authType}>
            {({ getFieldValue }) => {
              const authType = getFieldValue('authType');
              const fields = AUTH_FIELDS[authType] || [];
              return fields.map(f => (
                <Form.Item key={f.name} name={['authConfig', f.name]} label={f.label}>
                  <Input.Password placeholder={f.label} visibilityToggle={f.secret} />
                </Form.Item>
              ));
            }}
          </Form.Item>
          <Space>
            <Button onClick={() => setCurrent(0)}>Back</Button>
            <Button type="primary" htmlType="submit">Next</Button>
          </Space>
        </Form>
      ),
    },
    {
      title: 'Endpoints',
      icon: <ApiOutlined />,
      content: (
        <div>
          <Alert
            type="info"
            message="Configure API operations your EHR supports. These are the endpoints the platform will call when routing data to your system."
            style={{ marginBottom: 16 }}
          />
          {endpoints.map((ep, i) => (
            <Card key={i} size="small" style={{ marginBottom: 12 }}
              title={`Operation ${i + 1}`}
              extra={<Button size="small" danger onClick={() => removeEndpoint(i)}>Remove</Button>}>
              <Space wrap>
                <Select value={ep.operationType} style={{ width: 200 }}
                  onChange={(v) => updateEndpoint(i, 'operationType', v)}>
                  {['SAVE_PATIENT','UPDATE_PATIENT','GET_PATIENT','SAVE_ENCOUNTER',
                    'UPDATE_ENCOUNTER','SAVE_OBSERVATION','SAVE_CONDITION'].map(op => (
                    <Option key={op} value={op}>{op}</Option>
                  ))}
                </Select>
                <Select value={ep.httpMethod} style={{ width: 90 }}
                  onChange={(v) => updateEndpoint(i, 'httpMethod', v)}>
                  {['GET','POST','PUT','PATCH'].map(m => (
                    <Option key={m} value={m}>{m}</Option>
                  ))}
                </Select>
                <Input value={ep.pathTemplate} placeholder="/patients/{patientId}"
                  style={{ width: 220 }}
                  onChange={(e) => updateEndpoint(i, 'pathTemplate', e.target.value)} />
              </Space>
            </Card>
          ))}
          <Button onClick={addEndpoint} style={{ marginBottom: 16 }}>+ Add Operation</Button>
          <Divider />
          <Space>
            <Button onClick={() => setCurrent(1)}>Back</Button>
            <Button type="primary" onClick={() => setCurrent(3)}>Next</Button>
          </Space>
        </div>
      ),
    },
    {
      title: 'Test',
      icon: <CheckCircleOutlined />,
      content: (
        <div>
          <Paragraph>
            Test the connection to your EHR system before completing registration.
          </Paragraph>
          <Button type="default" loading={loading} onClick={testConnection}>
            Test Connection
          </Button>
          {testResult && (
            <Alert
              style={{ marginTop: 16 }}
              type={testResult.success ? 'success' : 'error'}
              message={testResult.message}
            />
          )}
          <Divider />
          <Space>
            <Button onClick={() => setCurrent(2)}>Back</Button>
            <Button type="primary" onClick={() => setCurrent(4)}>Next</Button>
          </Space>
        </div>
      ),
    },
    {
      title: 'Review',
      icon: <SettingOutlined />,
      content: (
        <div>
          <Card size="small" title="Registration Summary">
            <p><strong>EHR Code:</strong> {stepOne.ehrCode}</p>
            <p><strong>Name:</strong> {stepOne.displayName}</p>
            <p><strong>Base URL:</strong> {stepTwo.baseUrl}</p>
            <p><strong>Auth:</strong> {stepTwo.authType}</p>
            <p><strong>Endpoints:</strong> {endpoints.length} configured</p>
          </Card>
          <Divider />
          <Space>
            <Button onClick={() => setCurrent(3)}>Back</Button>
            <Button type="primary" loading={loading} onClick={submit}>
              Complete Registration
            </Button>
          </Space>
        </div>
      ),
    },
    {
      title: 'Done',
      icon: <CheckCircleOutlined />,
      content: (
        <Result
          status="success"
          title="EHR System Registered!"
          subTitle={`${stepOne.displayName} (${stepOne.ehrCode}) is now registered.`}
          extra={[
            <Alert
              key="key"
              type="warning"
              message="Your API Key — save this securely before leaving this page"
              description={
                <div>
                  <Paragraph style={{ marginBottom: 8 }}>
                    This key is shown <strong>only once</strong> and cannot be retrieved again.
                    Store it in a password manager or secrets vault immediately.
                  </Paragraph>
                  <Space>
                    <code style={{ fontSize: 14, wordBreak: 'break-all', letterSpacing: 1 }}>
                      {apiKeyVisible ? apiKey : maskedKey}
                    </code>
                    <Tooltip title={apiKeyVisible ? 'Hide key' : 'Reveal key'}>
                      <Button
                        icon={apiKeyVisible ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                        size="small"
                        onClick={() => setApiKeyVisible(v => !v)}
                      />
                    </Tooltip>
                    <Tooltip title="Copy to clipboard">
                      <Button
                        icon={<CopyOutlined />}
                        size="small"
                        onClick={copyApiKey}
                      >
                        Copy
                      </Button>
                    </Tooltip>
                  </Space>
                </div>
              }
            />,
            <Button key="portal" type="primary" onClick={() => window.location.href = '/dashboard'}>
              Go to Dashboard
            </Button>,
          ]}
        />
      ),
    },
  ];

  return (
    <div style={{ padding: 32, maxWidth: 800, margin: '0 auto' }}>
      <Title level={3}>Register Your EHR System</Title>
      <Steps current={current} style={{ marginBottom: 32 }}>
        {steps.map((s) => (
          <Step key={s.title} title={s.title} icon={s.icon} />
        ))}
      </Steps>
      <Card>{steps[current].content}</Card>
    </div>
  );
};
