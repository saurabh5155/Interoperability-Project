import React, { useEffect, useState } from 'react';
import {
  Table, Card, Tag, Button, Space, Popconfirm, message,
  Drawer, Descriptions, Badge, Tooltip
} from 'antd';
import {
  CheckCircleOutlined, StopOutlined, KeyOutlined,
  ApiOutlined, InfoCircleOutlined
} from '@ant-design/icons';
import { ehrApi, EhrRegistration, EhrEndpoint } from '../../api/ehrApi';

const statusColor: Record<string, string> = {
  ACTIVE: 'success',
  SUSPENDED: 'error',
  PENDING_REVIEW: 'warning',
  DEACTIVATED: 'default',
};

export const EhrManagement: React.FC = () => {
  const [ehrs, setEhrs] = useState<EhrRegistration[]>([]);
  const [endpoints, setEndpoints] = useState<EhrEndpoint[]>([]);
  const [drawerEhr, setDrawerEhr] = useState<EhrRegistration | null>(null);
  const [loading, setLoading] = useState(false);

  const loadEhrs = async () => {
    setLoading(true);
    const r = await ehrApi.listAll();
    setEhrs(r.data);
    setLoading(false);
  };

  useEffect(() => { loadEhrs(); }, []);

  const handleSuspend = async (ehrCode: string) => {
    await ehrApi.suspend(ehrCode);
    message.success(`${ehrCode} suspended`);
    loadEhrs();
  };

  const handleActivate = async (ehrCode: string) => {
    await ehrApi.activate(ehrCode);
    message.success(`${ehrCode} activated`);
    loadEhrs();
  };

  const handleRotateKey = async (ehrCode: string) => {
    const r = await ehrApi.rotateKey(ehrCode);
    Modal_alert(r.data.apiKey);
  };

  const Modal_alert = (key: string) => {
    message.warning(`New API key (copy now, won't be shown again): ${key}`, 10);
  };

  const handleViewEndpoints = async (ehr: EhrRegistration) => {
    setDrawerEhr(ehr);
    const r = await ehrApi.listEndpoints(ehr.ehrCode);
    setEndpoints(r.data);
  };

  const columns = [
    {
      title: 'EHR System',
      dataIndex: 'displayName',
      key: 'displayName',
      render: (name: string, record: EhrRegistration) => (
        <Space direction="vertical" size={0}>
          <strong>{name}</strong>
          <Tag>{record.ehrCode}</Tag>
        </Space>
      ),
    },
    { title: 'Organization', dataIndex: 'orgName', key: 'orgName' },
    { title: 'FHIR', dataIndex: 'fhirVersion', key: 'fhirVersion',
      render: (v: string) => <Tag color="blue">{v}</Tag> },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge status={statusColor[status] as any} text={status} />
      ),
    },
    { title: 'Auth', dataIndex: 'authType', key: 'authType',
      render: (t: string) => <Tag color="purple">{t}</Tag> },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: EhrRegistration) => (
        <Space>
          <Tooltip title="View Endpoints">
            <Button size="small" icon={<ApiOutlined />}
              onClick={() => handleViewEndpoints(record)} />
          </Tooltip>
          <Tooltip title="Rotate API Key">
            <Popconfirm title="Rotate API key?" onConfirm={() => handleRotateKey(record.ehrCode)}>
              <Button size="small" icon={<KeyOutlined />} />
            </Popconfirm>
          </Tooltip>
          {record.status === 'ACTIVE' ? (
            <Popconfirm title="Suspend this EHR?" onConfirm={() => handleSuspend(record.ehrCode)}>
              <Button size="small" danger icon={<StopOutlined />}>Suspend</Button>
            </Popconfirm>
          ) : (
            <Button size="small" icon={<CheckCircleOutlined />}
              onClick={() => handleActivate(record.ehrCode)}>Activate</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card title="Registered EHR Systems" loading={loading}>
        <Table dataSource={ehrs} columns={columns} rowKey="id" />
      </Card>

      <Drawer
        title={`Endpoints: ${drawerEhr?.displayName}`}
        open={!!drawerEhr}
        onClose={() => { setDrawerEhr(null); setEndpoints([]); }}
        width={600}
      >
        {endpoints.map((ep) => (
          <Card key={ep.id} size="small" style={{ marginBottom: 12 }}
            title={<Tag color="blue">{ep.operationType}</Tag>}
            extra={<Tag color={ep.active ? 'green' : 'red'}>{ep.active ? 'Active' : 'Inactive'}</Tag>}
          >
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Method">
                <Tag color="purple">{ep.httpMethod}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Path">{ep.pathTemplate}</Descriptions.Item>
              <Descriptions.Item label="Timeout">{ep.timeoutMs}ms</Descriptions.Item>
              <Descriptions.Item label="Retries">{ep.retryCount}</Descriptions.Item>
            </Descriptions>
          </Card>
        ))}
      </Drawer>
    </div>
  );
};
