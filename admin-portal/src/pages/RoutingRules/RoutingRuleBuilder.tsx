import React, { useState, useEffect } from 'react';
import {
  Form, Select, Button, Card, Space, Switch, InputNumber,
  Modal, message, Tag, Table, Tooltip
} from 'antd';
import { PlusOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import { ehrApi, EhrRegistration } from '../../api/ehrApi';
import { subscriptionApi, RoutingRule, CreateRoutingRuleRequest } from '../../api/subscriptionApi';

const { Option } = Select;

const RESOURCE_TYPES = ['PATIENT', 'ENCOUNTER', 'CONDITION', 'OBSERVATION', 'MEDICATION_REQUEST'];
const OPERATIONS = [
  'SAVE_PATIENT', 'UPDATE_PATIENT', 'GET_PATIENT',
  'SAVE_ENCOUNTER', 'UPDATE_ENCOUNTER',
  'SAVE_OBSERVATION', 'SAVE_CONDITION',
];

export const RoutingRuleBuilder: React.FC = () => {
  const [ehrs, setEhrs] = useState<EhrRegistration[]>([]);
  const [rules, setRules] = useState<RoutingRule[]>([]);
  const [selectedSource, setSelectedSource] = useState<string>('');
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<CreateRoutingRuleRequest>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    ehrApi.listAll().then((r) => setEhrs(r.data));
  }, []);

  const loadRules = (sourceEhrCode: string) => {
    setSelectedSource(sourceEhrCode);
    subscriptionApi.listRoutingRules(sourceEhrCode).then((r) => setRules(r.data));
  };

  const handleCreateRule = async (values: CreateRoutingRuleRequest) => {
    setLoading(true);
    try {
      await subscriptionApi.createRoutingRule({ ...values, sourceEhrCode: selectedSource });
      message.success('Routing rule created');
      loadRules(selectedSource);
      setModalOpen(false);
      form.resetFields();
    } catch (e: any) {
      message.error(e.response?.data?.message || 'Failed to create rule');
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async (id: string, active: boolean) => {
    await subscriptionApi.toggleRoutingRule(id, active);
    loadRules(selectedSource);
  };

  const columns = [
    { title: 'Target EHR', dataIndex: 'targetEhrCode', key: 'targetEhrCode',
      render: (code: string) => <Tag color="blue">{code}</Tag> },
    { title: 'Resource', dataIndex: 'resourceType', key: 'resourceType',
      render: (rt: string) => <Tag color="green">{rt}</Tag> },
    { title: 'Operation', dataIndex: 'targetOperation', key: 'targetOperation' },
    { title: 'Mode', dataIndex: 'transformMode', key: 'transformMode',
      render: (mode: string) => (
        <Tag color={mode === 'SYNC' ? 'purple' : 'orange'}>{mode}</Tag>
      )},
    { title: 'Priority', dataIndex: 'priority', key: 'priority' },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      render: (active: boolean, record: RoutingRule) => (
        <Switch checked={active} onChange={(v) => handleToggle(record.id, v)} />
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card title="Routing Rules Configuration">
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          {/* Source EHR selector */}
          <Card size="small" title="Select Source EHR">
            <Select
              placeholder="Select source EHR system"
              style={{ width: 320 }}
              onChange={loadRules}
              showSearch
            >
              {ehrs.filter(e => e.status === 'ACTIVE').map((ehr) => (
                <Option key={ehr.ehrCode} value={ehr.ehrCode}>
                  {ehr.displayName} ({ehr.ehrCode})
                </Option>
              ))}
            </Select>
          </Card>

          {/* Active routes visualization */}
          {selectedSource && (
            <Card
              size="small"
              title={`Active Routes: ${selectedSource} → Target EHRs`}
              extra={
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setModalOpen(true)}
                >
                  Add Target EHR
                </Button>
              }
            >
              {rules.length === 0 ? (
                <div style={{ textAlign: 'center', color: '#999', padding: 32 }}>
                  No routing rules configured. Click "Add Target EHR" to start routing data.
                </div>
              ) : (
                <Table
                  dataSource={rules}
                  columns={columns}
                  rowKey="id"
                  size="small"
                  pagination={false}
                />
              )}
            </Card>
          )}
        </Space>
      </Card>

      {/* Add routing rule modal */}
      <Modal
        title={`Add Routing Target for ${selectedSource}`}
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        footer={null}
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateRule}>
          <Form.Item name="targetEhrCode" label="Target EHR" rules={[{ required: true }]}>
            <Select placeholder="Select target EHR" showSearch>
              {ehrs
                .filter(e => e.status === 'ACTIVE' && e.ehrCode !== selectedSource)
                .map((ehr) => (
                  <Option key={ehr.ehrCode} value={ehr.ehrCode}>
                    {ehr.displayName} ({ehr.ehrCode})
                  </Option>
                ))}
            </Select>
          </Form.Item>

          <Form.Item name="resourceType" label="Resource Type" rules={[{ required: true }]}>
            <Select placeholder="Select resource type">
              {RESOURCE_TYPES.map((rt) => (
                <Option key={rt} value={rt}>{rt}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="targetOperation" label="Target Operation" rules={[{ required: true }]}>
            <Select placeholder="Select operation">
              {OPERATIONS.map((op) => (
                <Option key={op} value={op}>{op}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="transformMode" label="Transform Mode" initialValue="SYNC">
            <Select>
              <Option value="SYNC">SYNC — Wait for response</Option>
              <Option value="ASYNC">ASYNC — Fire and forget (Kafka)</Option>
            </Select>
          </Form.Item>

          <Form.Item name="priority" label="Priority (lower = first)" initialValue={0}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={loading}>
                Create Rule
              </Button>
              <Button onClick={() => setModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
