use std::collections::HashMap;
use std::sync::Mutex;

use jni::objects::{JObject, JString, JValue};
use jni::sys::jint;
use jni::JNIEnv;
use json::JsonValue;
use once_cell::sync::Lazy;
use reqwest::header::{HeaderMap, HeaderValue};
use serde::Deserialize;

// To include this:
// Delete app/build/intermediates/merged_jni_libs and rerun
// This can be automated in your Android Studio settings

#[derive(Deserialize, Debug)]
struct IFunnyImage {
    comments: usize,
    smiles: usize,
    title: String,
    url: String,
}

#[derive(Deserialize, Default, Debug)]
struct IFunnyImages {
    #[serde(rename = "pageCount")]
    page_count: usize,
    items: Vec<IFunnyImage>,
}

static IMAGES: Lazy<Mutex<IFunnyImages>> = Lazy::new(|| Mutex::new(IFunnyImages::default()));
static IMAGEBUFFERS: Lazy<Mutex<HashMap<usize, Vec<u8>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));
static HEADER: Lazy<HeaderMap> = Lazy::new(|| {
    let mut h = HeaderMap::new();
    h.insert(
        "User-Agent",
        HeaderValue::from_static(
            "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/116.0",
        ),
    );
    h.insert("x-requested-with", HeaderValue::from_static("fetch"));
    h.insert(
        "x-csrf-token",
        HeaderValue::from_static("237ec3775880187bdc5c9c3a1c86d104"),
    );
    h.insert(
        "Cookie",
        HeaderValue::from_static("x-csrf-token=237ec3775880187bdc5c9c3a1c86d104; CID=7a8480916dcaba36370b7d6da570781a07b59e0fc9d0a11bf7c587cf8e1bebdd.05179389627a4c65"),
    );
    h
});

macro_rules! tojstr {
    ($env: expr, $str:expr) => {
        $env.new_string($str).unwrap()
    };
}

macro_rules! log {
    ($env:expr, $val:expr) => {
        // $env.exception_clear().unwrap_or(());
        $env.call_static_method(
            "android/util/Log",
            "i",
            "(Ljava/lang/String;Ljava/lang/String;)I",
            unsafe {
                &[
                    JValue::Object(&JObject::from_raw(tojstr!($env, "i").into_raw())),
                    JValue::Object(&JObject::from_raw(tojstr!($env, $val).into_raw())),
                ]
            },
        )
        .unwrap();
    };
}

fn get_unit<'a>(env: &mut JNIEnv<'a>) -> JObject<'a> {
    env.new_object("kotlin/Unit", "()V", &[]).unwrap()
}
fn get_int<'a>(env: &mut JNIEnv<'a>, i: i32) -> JObject<'a> {
    env.new_object("java/lang/Integer", "(I)V", &[JValue::Int(i)])
        .unwrap()
}

fn as_jobj(s: JString) -> JObject {
    unsafe { JObject::from_raw(s.into_raw()) }
}

const CALLBACK_SIG: &str = "(Ljava/lang/Object;)Ljava/lang/Object;";

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_initImages(
    mut env: JNIEnv,
    _obj: JObject,
    callback: JObject, // Fn(i32)
) {
    let c = reqwest::blocking::Client::new();
    let req = c
        .get("https://ifunny.co/api/v1/feeds/featured")
        .headers(HEADER.clone())
        .query(&[("page", 1)])
        .build()
        .unwrap();
    log!(env, format!("{req:?}"));
    let json = c.execute(req).unwrap().text().unwrap();

    log!(env, &json);
    let parsed: Result<IFunnyImages, _> = serde_json::from_str(&json);
    if parsed.is_err() {
        log!(env, parsed.unwrap_err().to_string());
        return;
    }
    *IMAGES.lock().unwrap() = parsed.unwrap();

    let u = get_int(&mut env, IMAGES.lock().unwrap().items.len() as i32);

    log!(env, "Main call");

    match env.call_method(callback, "apply", CALLBACK_SIG, &[JValue::Object(&u)]) {
        Ok(_) => (),
        Err(e) => {
            log!(env, e.to_string());
        }
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_imageCount(
    _env: JNIEnv,
    _obj: JObject,
) -> i32 {
    // log!(env, num.to_string());
    IMAGES.lock().unwrap().items.len() as i32
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_getImage<'local>(
    mut env: JNIEnv<'local>,
    _obj: JObject,
    num: jint,
    callback: JObject,
) {
    if num < 0 {
        return;
    }
    log!(env, "anything?");
    let ind = num as usize;
    let exists = { IMAGEBUFFERS.lock().unwrap().get(&ind).is_some() };
    let b = if exists {
        log!(env, "exists");
        env.byte_array_from_slice(IMAGEBUFFERS.lock().unwrap().get(&ind).unwrap())
            .unwrap()
    } else {
        log!(env, "get web");
        let cur_img = IMAGES.lock().unwrap().items.len();
        if cur_img + 10 <= ind {
            env.byte_array_from_slice(&[]).unwrap()
        } else {
            if cur_img <= ind {
                let c = reqwest::blocking::Client::new();
                let page = ind / 10 + 1;

                let json = c
                    .get("https://ifunny.co/api/v1/feeds/featured")
                    .headers(HEADER.clone())
                    .query(&[("page", page)])
                    .send()
                    .unwrap();

                let images: Result<IFunnyImages, _> = serde_json::from_str(&json.text().unwrap());
                if images.is_err() {
                    log!(env, images.unwrap_err().to_string());
                    return;
                }
                let images = images.unwrap();
                IMAGES
                    .lock()
                    .unwrap()
                    .items
                    .extend(images.items.into_iter())
            }
            let path = &IMAGES.lock().unwrap().items[ind].url;
            let c = reqwest::blocking::Client::new();
            if let Ok(resp) = c.get(path).send() {
                if let Ok(resp_bytes) = resp.bytes() {
                    IMAGEBUFFERS
                        .lock()
                        .unwrap()
                        .insert(ind, resp_bytes.to_vec());
                    log!(env, format!("bytes: {}", resp_bytes.len()));
                    env.byte_array_from_slice(&resp_bytes).unwrap()
                } else {
                    env.byte_array_from_slice(&[]).unwrap()
                }
            } else {
                env.byte_array_from_slice(&[]).unwrap()
            }
        }
    };

    log!(env, "pre call??");

    match env.call_method(
        callback,
        "apply",
        CALLBACK_SIG,
        &[JValue::Object(&unsafe { JObject::from_raw(b.into_raw()) })],
    ) {
        Ok(_) => {
            log!(env, "Function good");
        }
        Err(e) => {
            log!(env, e.to_string());
        }
    }
    log!(env, "what??");
}
