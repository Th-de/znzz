import { NotifyItem } from '../model/Models';

/**
 * 通知消息按“需求”分组工具。
 *
 * 说明：当前后端 notify 表只有企业级通知、没有 demandId 字段，
 * 通知标题/内容里的需求引用形如 “#123” 与 “需求「xxx」…”。
 * 因此这里用启发式解析：标题/内容中提取 #数字 作为需求 ID，
 * 从内容「…」中提取需求名称；后端补齐 demandId 后可改为按字段精确分组。
 */

export interface NotifyGroup {
  key: string;          // 'd<需求ID>' 或 'n<通知ID>'
  title: string;        // 需求名称（无需求引用时用通知标题）
  latestContent: string;
  latestTime: string;
  count: number;
  items: NotifyItem[];
}

/** 从文本中提取第一个 #数字 需求引用，找不到返回 '' */
function extractDemandId(text: string): string {
  if (text === undefined || text === null || text.length === 0) {
    return '';
  }
  for (let i = 0; i < text.length; i++) {
    if (text.charAt(i) === '#') {
      let j: number = i + 1;
      let digits: string = '';
      while (j < text.length && text.charAt(j) >= '0' && text.charAt(j) <= '9') {
        digits = digits + text.charAt(j);
        j++;
      }
      if (digits.length > 0) {
        return digits;
      }
    }
  }
  return '';
}

/** 从内容里提取第一个「名称」作为需求名称 */
function extractName(text: string): string {
  if (text === undefined || text === null) {
    return '';
  }
  const s: number = text.indexOf('「');
  if (s < 0) {
    return '';
  }
  const e: number = text.indexOf('」', s + 1);
  if (e < 0) {
    return '';
  }
  return text.substring(s + 1, e);
}

/** 单条通知归属 key：需求引用优先，否则按通知 id 自成一组 */
export function keyOfNotify(n: NotifyItem): string {
  const tid: string = extractDemandId(n.title ?? '');
  const cid: string = extractDemandId(n.content ?? '');
  const id: string = tid.length > 0 ? tid : cid;
  if (id.length > 0) {
    return 'd' + id;
  }
  return 'n' + (n.id === undefined ? 'x' : String(n.id));
}

/** 组标题：需求名称，取不到时回退 “需求 #id” */
function titleOf(key: string, n: NotifyItem): string {
  if (key.length > 1 && key.charAt(0) === 'd') {
    const name: string = extractName(n.content ?? '');
    if (name.length > 0) {
      return name;
    }
    return '需求 #' + key.substring(1);
  }
  return n.title ?? '';
}

/** 将通知列表合并成分组列表（按每个分组最新消息时间倒序） */
export function groupNotifies(list: NotifyItem[]): NotifyGroup[] {
  const out: NotifyGroup[] = [];
  for (let i = 0; i < list.length; i++) {
    const n: NotifyItem = list[i];
    const key: string = keyOfNotify(n);
    let idx: number = -1;
    for (let g = 0; g < out.length; g++) {
      if (out[g].key === key) {
        idx = g;
        break;
      }
    }
    const time: string = n.createdAt ?? '';
    if (idx < 0) {
      const items: NotifyItem[] = [];
      items.push(n);
      const grp: NotifyGroup = {
        key: key,
        title: titleOf(key, n),
        latestContent: n.content ?? '',
        latestTime: time,
        count: 1,
        items: items
      };
      out.push(grp);
    } else {
      const g: NotifyGroup = out[idx];
      const items: NotifyItem[] = g.items.slice();
      items.push(n);
      let latestContent: string = g.latestContent;
      let latestTime: string = g.latestTime;
      if (time.length > 0 && time >= latestTime) {
        latestTime = time;
        latestContent = n.content ?? '';
      }
      let title: string = g.title;
      const demandName: string = extractName(n.content ?? '');
      if (demandName.length > 0) {
        title = demandName;
      }
      const ng: NotifyGroup = {
        key: g.key,
        title: title,
        latestContent: latestContent,
        latestTime: latestTime,
        count: g.count + 1,
        items: items
      };
      out[idx] = ng;
    }
  }
  // 按最新时间倒序
  for (let a = 0; a < out.length; a++) {
    for (let b = a + 1; b < out.length; b++) {
      if (out[b].latestTime > out[a].latestTime) {
        const tmp: NotifyGroup = out[a];
        out[a] = out[b];
        out[b] = tmp;
      }
    }
  }
  return out;
}