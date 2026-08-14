import { memo, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import { ConfigProvider, message } from 'antd';
import { ConstantProvider } from '@/hooks';
import * as constants from "@/utils/constants";
import { setMessage } from '@/utils';

const appFontFamily =
  '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", Arial, sans-serif';

// Layout 组件：应用的主要布局结构
const Layout: GenieType.FC = memo(() => {
  const [messageApi, messageContent] = message.useMessage();

  useEffect(() => {
    // 初始化全局 message
    setMessage(messageApi);
  }, [messageApi]);

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#1D1D1F',
          colorPrimaryHover: '#000000',
          colorText: '#1D1D1F',
          colorTextSecondary: '#6E6E73',
          colorBorder: '#E5E5EA',
          colorBgContainer: '#FFFFFF',
          colorBgLayout: '#F5F5F7',
          borderRadius: 10,
          borderRadiusLG: 14,
          controlHeight: 36,
          controlHeightLG: 40,
          fontFamily: appFontFamily,
          boxShadowSecondary: '0 2px 8px rgba(0, 0, 0, 0.06)',
        },
        components: {
          Button: {
            borderRadius: 10,
            controlHeight: 36,
            controlHeightLG: 40,
          },
          Input: {
            borderRadius: 10,
            controlHeight: 36,
            controlHeightLG: 40,
          },
          Modal: {borderRadiusLG: 14,},
          Drawer: {paddingLG: 20,},
        },
      }}
    >
      {messageContent}
      {/* 暂时只有静态的 */}
      <ConstantProvider value={constants}>
        <Outlet />
      </ConstantProvider>
    </ConfigProvider>
  );
});

Layout.displayName = 'Layout';

export default Layout;
