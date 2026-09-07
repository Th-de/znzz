import http from '@ohos.net.http';
import { BusinessError } from '@ohos.base';
import { Constants } from './Constants';
import { Session } from './Session';
import { ApiResult } from '../model/Models';

/** 业务/网络异常，code 为后端 R.code 或 HTTP 状态码 */
export class ApiException extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.code = code;
  }
}

interface HeadersMap {
  'Content-Type': string;
  'Authorization'?: string;
}

/**
 * 网络请求封装：
 * - 自动拼接局域网后端地址 Constants.BASE_URL
 * - 自动携带 Authorization: Bearer <token>
 * - 统一解开后端 R<T> 包裹：code===0 时 resolve(data)，否则 reject(ApiException)
 * - 统一 JSON 序列化/反序列化
 */
export class HttpUtil {
  /** 通用请求，T 为后端 R.data 的类型 */
  static request<T>(path: string, method: http.RequestMethod, bodyJson?: string): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const url: string = Constants.BASE_URL + path;
      const token: string = Session.token();
      const headers: HeadersMap = { 'Content-Type': 'application/json' };
      if (token.length > 0) {
        headers.Authorization = 'Bearer ' + token;
      }
      const options: http.HttpRequestOptions = {
        method: method,
        header: headers,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: Constants.CONNECT_TIMEOUT,
        readTimeout: Constants.READ_TIMEOUT
      };
      if (bodyJson !== undefined && bodyJson !== '') {
        options.extraData = bodyJson;
      }
      const req: http.HttpRequest = http.createHttp();
      req.request(url, options, (err: BusinessError, resp: http.HttpResponse) => {
        req.destroy();
        if (err && err.code) {
          reject(new ApiException(err.code, '网络错误：' + err.message));
          return;
        }
        if (resp.responseCode < 200 || resp.responseCode >= 300) {
          let msg: string = 'HTTP ' + resp.responseCode;
          try {
            const raw2: string | Object | ArrayBuffer = resp.result;
            if (typeof raw2 === 'string') {
              const parsed: ApiResult<Object> = JSON.parse(raw2) as ApiResult<Object>;
              if (parsed.message !== undefined && parsed.message !== '') {
                msg = parsed.message;
              }
            }
          } catch (e) {
            msg = '服务器返回异常(' + resp.responseCode + ')';
          }
          reject(new ApiException(resp.responseCode, msg));
          return;
        }
        const raw: string | Object | ArrayBuffer = resp.result;
        if (typeof raw !== 'string') {
          reject(new ApiException(-1, '响应格式错误'));
          return;
        }
        try {
          const result: ApiResult<T> = JSON.parse(raw) as ApiResult<T>;
          if (result.code !== 0) {
                        const rawMsg: string | undefined = result.message;
            const errMsg: string = (rawMsg === undefined || rawMsg === '') ? '请求失败' : rawMsg;
            reject(new ApiException(result.code, errMsg));
            return;
          }
          resolve(result.data as T);
        } catch (e) {
          reject(new ApiException(-1, '数据解析失败'));
        }
      });
    });
  }

  /** GET 请求 */
  static get<T>(path: string): Promise<T> {
    return HttpUtil.request<T>(path, http.RequestMethod.GET);
  }

  /** POST 请求并返回 data */
  static postData<T>(path: string, body?: Object): Promise<T> {
    const json: string = body === undefined ? '' : JSON.stringify(body);
    return HttpUtil.request<T>(path, http.RequestMethod.POST, json);
  }

  /** POST 请求，只关心成功与否（R<Void>） */
  static post(path: string, body?: Object): Promise<void> {
    const json: string = body === undefined ? '' : JSON.stringify(body);
    return HttpUtil.request<Object>(path, http.RequestMethod.POST, json)
      .then((v: Object) => {
        // 不关心返回值
        return;
      });
  }
}