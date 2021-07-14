// import Vue from 'vue';
import axios from 'axios';
import https from 'https';
import { Message, Spin, Modal } from 'view-design';
import Storage from './storage';
import config from '@/config';
import router from '../router/index.js';
import store from '../vuex/store';
import { handleRefreshToken } from '@/api/index';
const qs = require('qs');
export const buyerUrl =
  process.env.NODE_ENV === 'development'
    ? config.api_dev.buyer
    : config.api_prod.buyer;
export const commonUrl =
  process.env.NODE_ENV === 'development'
    ? config.api_dev.common
    : config.api_prod.common;
export const managerUrl =
  process.env.NODE_ENV === 'development'
    ? config.api_dev.manager
    : config.api_prod.manager;
export const sellerUrl =
  process.env.NODE_ENV === 'development'
    ? config.api_dev.seller
    : config.api_prod.seller;
// 创建axios实例
var isRefreshToken = 0;
const refreshToken = getTokenDebounce();
const service = axios.create({
  timeout: 10000, // 请求超时时间
  baseURL: buyerUrl, // API
  httpsAgent: new https.Agent({
    rejectUnauthorized: false
  }),
  paramsSerializer: params =>
    qs.stringify(params, {
      arrayFormat: 'repeat'
    })
});

// request拦截器
service.interceptors.request.use(
  config => {
    const { loading } = config;
    // 如果是put/post请求，用qs.stringify序列化参数
    const isPutPost = config.method === 'put' || config.method === 'post';
    const isJson = config.headers['Content-Type'] === 'application/json';
    const isFile = config.headers['Content-Type'] === 'multipart/form-data';
    if (isPutPost && isJson) {
      config.data = JSON.stringify(config.data);
    }
    if (isPutPost && !isFile && !isJson) {
      config.data = qs.stringify(config.data, {
        arrayFormat: 'repeat'
      });
    }
    /** 配置全屏加载 */
    if (process.client && loading !== false) {
      config.loading = Spin.show();
    }

    const uuid = Storage.getItem('uuid');
    config.headers['uuid'] = uuid;

    // 获取访问Token
    let accessToken = Storage.getItem('accessToken');
    if (accessToken && config.needToken) {
      config.headers['accessToken'] = accessToken;
      // 解析当前token时间
      let jwtData = JSON.parse(
        decodeURIComponent(escape(window.atob(accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))))
      );
      if (jwtData.exp < Math.round(new Date() / 1000)) {
        refresh(config)
      }
    }

    return config;
  },
  error => {
    Promise.reject(error);
  }
);

async function refresh (error) {
  const getTokenRes = await refreshToken();
  if (getTokenRes === 'success') {
    // 刷新token
    if (isRefreshToken === 1) {
      error.response.config.headers.accessToken = Storage.getItem(
        'accessToken'
      );
      return service(error.response.config);
    } else {
      router.go(0);
    }
  } else {
    Storage.removeItem('accessToken');
    Storage.removeItem('refreshToken');
    Storage.removeItem('userInfo');
    Storage.setItem('cartNum', 0);
    store.commit('SET_CARTNUM', 0);
    Modal.confirm({
      title: '请登录',
      content: '<p>请登录后执行此操作</p>',
      okText: '立即登录',
      cancelText: '继续浏览',
      onOk: () => {
        router.push({
          path: '/login',
          query: {
            rePath: router.history.current.path,
            query: JSON.stringify(router.history.current.query)
          }
        });
      },
      onCancel: () => {
        router.go(0)
        Modal.remove();
      }
    });
  }
}

// respone拦截器
service.interceptors.response.use(
  async response => {
    await closeLoading(response);

    return response.data;
  },
  async error => {
    if (process.server) return Promise.reject(error);
    await closeLoading(error);
    const errorResponse = error.response || {};
    const errorData = errorResponse.data || {};

    if (errorResponse.status === 403) {
      isRefreshToken++;

      if (isRefreshToken === 1) {
        refresh(error)
        isRefreshToken = 0;
      }
    } else if (errorResponse.status === 404) {
      // 避免刷新token时也提示报错信息
    } else {
      if (error.message) {
        let _message =
          error.code === 'ECONNABORTED'
            ? '连接超时，请稍候再试！'
            : '网络错误，请稍后再试！';
        Message.error(errorData.message || _message);
      }
    }
    return Promise.reject(error);
  }
);

/**
 * 关闭全局加载
 * @param target
 */
const closeLoading = target => {
  if (!target.config || !target.config.loading) return true;
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      target.config.loading.hide();
      resolve();
    }, 200);
  });
};

export const Method = {
  GET: 'get',
  POST: 'post',
  PUT: 'put',
  DELETE: 'delete'
};

export default function request (options) {
  // 如果是服务端或者是请求的刷新token，不需要检查token直接请求。
  //   if (process.server || options.url.indexOf('passport/token') !== -1) {
  return service(options);
  //   }
  // service(options).then(resolve).catch(reject)
}

// 防抖闭包来一波
function getTokenDebounce () {
  let lock = false;
  let success = false;
  return function () {
    if (!lock) {
      lock = true;
      let oldRefreshToken = Storage.getItem('refreshToken');
      handleRefreshToken(oldRefreshToken)
        .then(res => {
          if (res.success) {
            let { accessToken, refreshToken } = res.result;
            Storage.setItem('accessToken', accessToken);
            Storage.setItem('refreshToken', refreshToken);

            success = true;
            lock = false;
          } else {
            success = false;
            lock = false;
            // router.push('/login')
          }
        })
        .catch(err => {
          console.log(err);
          success = false;
          lock = false;
        });
    }
    return new Promise(resolve => {
      // 一直看lock,直到请求失败或者成功
      const timer = setInterval(() => {
        if (!lock) {
          clearInterval(timer);
          if (success) {
            resolve('success');
          } else {
            resolve('fail');
          }
        }
      }, 500); // 轮询时间间隔
    });
  };
}
