import { memo, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import { message } from 'antd';
import { ConstantProvider } from '@/hooks';
import * as constants from "@/utils/constants";
import { setMessage } from '@/utils';

// Layout 组件：应用的主要布局结构。主题与 locale 统一在 App.tsx 的 ConfigProvider 配置。
const Layout: GenieType.FC = memo(() => {
  const [messageApi, messageContent] = message.useMessage();

  useEffect(() => {
    // 初始化全局 message
    setMessage(messageApi);
  }, [messageApi]);

  return (
    <>
      {messageContent}
      {/* 暂时只有静态的 */}
      <ConstantProvider value={constants}>
        <Outlet />
      </ConstantProvider>
    </>
  );
});

Layout.displayName = 'Layout';

export default Layout;
