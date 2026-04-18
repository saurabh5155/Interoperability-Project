import React, { useState } from 'react';
import {
  Table, Card, Form, Select, DatePicker, Button, Tag, Space,
  Badge, Tooltip, Drawer, Descriptions
} from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { apiClient } from '../../api/apiClient';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const { Option } = Select;

interface AuditLog {
  id: string;
  correlationId: string;
  sourceEhrCode: string;
  targetEhrCode: string;
  resourceType: string;
  operation: string;
  status: string;
  errorMessage: string;
  durationMs: number;
  aiMappingUsed: boolean;
  aiConfidenceScore: number;
  mappingTemplateId: string;
  createdAt: string;
}

const statusConfig: Record<string, { color: string; badge: any }> = {
  SUCCESS: { color: 'green', badge: 'success' },
  FAILED: { color: 'red', badge: 'error' },
  PARTIAL_SUCCESS: { color: 'orange', badge: 'warning' },
  PENDING: { color: 'blue', badge: 'processing' },
};

export const TransformLogs: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
  const [form] = Form.useForm();

  const search = async (values: any) => {
    setLoading(true);
    const params = new URLSearchParams();
    if (values.sourceEhrCode) params.append('sourceEhrCode', values.sourceEhrCode);
    if (values.targetEhrCode) params.append('targetEhrCode', values.targetEhrCode);
    if (values.status) params.append('status', values.status);
    if (values.dateRange) {
      params.append('from', values.dateRange[0].toISOString());
      params.append('to', values.dateRange[1].toISOString());
    }
    const r = await apiClient.get<AuditLog[]>(`/api/v1/audit/search?${params}`);
    setLogs(r.data);
    setLoading(false);
  };

  const columns = [
    {
      title: 'Correlation ID',
      dataIndex: 'correlationId',
      key: 'correlationId',
      render: (id: string) => (
        <Tooltip title={id}>
          <code style={{ fontSize: 11 }}>{id.substring(0, 8)}...</code>
        </Tooltip>
      ),
    },
    {
      title: 'Route',
      key: 'route',
      render: (_: any, r: AuditLog) => (
        <Space>
          <Tag>{r.sourceEhrCode}</Tag>→<Tag>{r.targetEhrCode}</Tag>
        </Space>
      ),
    },
    { title: 'Resource', dataIndex: 'resourceType', key: 'resourceType',
      render: (t: string) => <Tag color="blue">{t}</Tag> },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => (
        <Badge status={statusConfig[s]?.badge || 'default'} text={s} />
      ),
    },
    {
      title: 'AI Used',
      dataIndex: 'aiMappingUsed',
      key: 'ai',
      render: (used: boolean, r: AuditLog) => used ? (
        <Tooltip title={`Confidence: ${(r.aiConfidenceScore * 100).toFixed(0)}%`}>
          <Tag color="purple">AI ({(r.aiConfidenceScore * 100).toFixed(0)}%)</Tag>
        </Tooltip>
      ) : <Tag>Rules</Tag>,
    },
    { title: 'Duration', dataIndex: 'durationMs', key: 'durationMs',
      render: (ms: number) => `${ms}ms` },
    { title: 'Time', dataIndex: 'createdAt', key: 'createdAt',
      render: (t: string) => dayjs(t).format('MM/DD HH:mm:ss') },
    {
      title: '',
      key: 'action',
      render: (_: any, record: AuditLog) => (
        <Button size="small" onClick={() => setSelectedLog(record)}>Details</Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card title="Transformation Audit Logs">
        <Form form={form} layout="inline" onFinish={search} style={{ marginBottom: 16 }}>
          <Form.Item name="sourceEhrCode">
            <Select placeholder="Source EHR" allowClear style={{ width: 160 }}>
              <Option value="ECW">ECW</Option>
              <Option value="OMNIONE">OMNIONE</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="Status" allowClear style={{ width: 140 }}>
              {['SUCCESS', 'FAILED', 'PARTIAL_SUCCESS'].map(s => (
                <Option key={s} value={s}>{s}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="dateRange">
            <RangePicker showTime />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              Search
            </Button>
          </Form.Item>
        </Form>

        <Table dataSource={logs} columns={columns} rowKey="id"
          loading={loading} pagination={{ pageSize: 20 }} />
      </Card>

      <Drawer title="Transformation Details" open={!!selectedLog}
        onClose={() => setSelectedLog(null)} width={600}>
        {selectedLog && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Correlation ID">
              <code>{selectedLog.correlationId}</code>
            </Descriptions.Item>
            <Descriptions.Item label="Source → Target">
              {selectedLog.sourceEhrCode} → {selectedLog.targetEhrCode}
            </Descriptions.Item>
            <Descriptions.Item label="Resource">{selectedLog.resourceType}</Descriptions.Item>
            <Descriptions.Item label="Operation">{selectedLog.operation}</Descriptions.Item>
            <Descriptions.Item label="Status">
              <Badge status={statusConfig[selectedLog.status]?.badge || 'default'}
                text={selectedLog.status} />
            </Descriptions.Item>
            <Descriptions.Item label="Duration">{selectedLog.durationMs}ms</Descriptions.Item>
            <Descriptions.Item label="AI Mapping">
              {selectedLog.aiMappingUsed
                ? `Yes (confidence: ${(selectedLog.aiConfidenceScore * 100).toFixed(1)}%)`
                : 'No (rules-based)'}
            </Descriptions.Item>
            <Descriptions.Item label="Mapping Template">
              {selectedLog.mappingTemplateId || 'N/A'}
            </Descriptions.Item>
            {selectedLog.errorMessage && (
              <Descriptions.Item label="Error">
                <code style={{ color: 'red' }}>{selectedLog.errorMessage}</code>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Timestamp">{selectedLog.createdAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};
