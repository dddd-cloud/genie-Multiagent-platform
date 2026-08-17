import React from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import { RouterProvider } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import router from './router';

const appFontFamily =
  '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", Arial, sans-serif';

const theme = {
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
    Modal: { borderRadiusLG: 14 },
    Drawer: { paddingLG: 20 },
  },
};

/**
 * AuthProvider lives inside the router tree (root route element)
 * so auth flows can use useNavigate / useLocation.
 */
const App: GenieType.FC = React.memo(() => {
  return (
    <ConfigProvider locale={zhCN} theme={theme}>
      {/* antd <App> renders a wrapper div — it must keep the 100% height chain. */}
      <AntdApp className="h-full">
        <RouterProvider router={router} />
      </AntdApp>
    </ConfigProvider>
  );
});

export default App;
