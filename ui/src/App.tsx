import React from 'react';
import { ConfigProvider } from 'antd';
import { RouterProvider } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import router from './router';

/**
 * AuthProvider lives inside the router tree (root route element)
 * so auth flows can use useNavigate / useLocation.
 */
const App: GenieType.FC = React.memo(() => {
  return (
    <ConfigProvider locale={zhCN}>
      <RouterProvider router={router} />
    </ConfigProvider>
  );
});

export default App;
