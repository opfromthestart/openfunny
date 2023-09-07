use std::collections::HashMap;
use std::sync::Mutex;

use jni::objects::{JObject, JString, JValue, JValueGen};
use jni::sys::{jint, jstring};
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
    id: String,
}

#[derive(Deserialize, Default, Debug)]
struct IFunnyImages {
    #[serde(rename = "pageCount")]
    page_count: usize,
    items: Vec<IFunnyImage>,
}
#[derive(Deserialize, Default, Debug)]
struct User {
    nick: String,
}
#[derive(Deserialize, Default, Debug)]
struct Comment {
    text: String,
    user: User,
}

#[derive(Deserialize, Default, Debug)]
struct Comments {
    items: Vec<Comment>,
}

static IMAGES: Lazy<Mutex<IFunnyImages>> = Lazy::new(|| Mutex::new(IFunnyImages::default()));
static IMAGEBUFFERS: Lazy<Mutex<HashMap<usize, Vec<u8>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));
static COMMENTBUFFERS: Lazy<Mutex<HashMap<usize, Vec<String>>>> =
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

fn as_obj<'a>(s: &str, env: &JNIEnv<'a>) -> JObject<'a> {
    as_jobj(env.new_string(s).unwrap())
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
    // log!(env, format!("{req:?}"));
    let json = c.execute(req).unwrap().text().unwrap();

    // log!(env, &json);
    let parsed: Result<IFunnyImages, _> = serde_json::from_str(&json);
    if parsed.is_err() {
        log!(env, parsed.unwrap_err().to_string());
        return;
    }
    *IMAGES.lock().unwrap() = parsed.unwrap();

    let u = get_int(&mut env, IMAGES.lock().unwrap().items.len() as i32);

    // log!(env, "Main call");

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

fn load_imgs<'a>(ind: usize, env: &mut JNIEnv<'a>) {
    let c = reqwest::blocking::Client::new();
    let page = ind / 10 + 1;

    let json = c
        .get(format!("https://ifunny.co/api/v1/feeds/featured"))
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
    // log!(env, "anything?");
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
                load_imgs(ind, &mut env);
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

    // log!(env, "pre call??");

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
    // log!(env, "what??");
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_getComments<'local>(
    mut env: JNIEnv<'local>,
    _obj: JObject,
    num: jint,
    callback: JObject,
) {
    if num < 0 {
        return;
    }
    // log!(env, "anything?");
    let ind = num as usize;
    let exists = { COMMENTBUFFERS.lock().unwrap().get(&ind).is_some() };
    let b = if exists {
        log!(env, "exists");
        let items = COMMENTBUFFERS.lock().unwrap().get(&ind).unwrap().len() as i32;
        let comments = env
            .new_object_array(items, "java/lang/String", &JObject::null())
            .unwrap();

        for (i, c) in COMMENTBUFFERS
            .lock()
            .unwrap()
            .get(&ind)
            .unwrap()
            .iter()
            .enumerate()
        {
            env.set_object_array_element(&comments, i as i32, as_obj(c, &env))
                .unwrap();
        }
        comments
    } else {
        log!(env, "get web");
        let cur_img = IMAGES.lock().unwrap().items.len();
        if cur_img + 10 <= ind {
            env.new_object_array(0, "java/lang/Object", &JObject::null())
                .unwrap()
        } else {
            if cur_img <= ind {
                load_imgs(ind, &mut env);
            }
            let id = IMAGES.lock().unwrap().items[ind].id.clone();
            let path = format!("https://ifunny.co/api/v1/content/{id}/comments?next=0");
            log!(env, &path);
            let c = reqwest::blocking::Client::new();
            if let Ok(resp) = c.get(path).headers(HEADER.clone()).send() {
                if let Ok(resp_str) = resp.text() {
                    log!(env, &resp_str);
                    let comment: Result<Comments, _> = serde_json::from_str(&resp_str);
                    if comment.is_err() {
                        log!(env, format!("{comment:?}"));
                        return;
                    }
                    let comment = comment.unwrap();
                    COMMENTBUFFERS.lock().unwrap().insert(
                        ind,
                        comment
                            .items
                            .into_iter()
                            .map(|c| format!("{}:{}", c.user.nick, c.text))
                            .collect(),
                    );
                    let items = COMMENTBUFFERS.lock().unwrap().get(&ind).unwrap().len() as i32;
                    let comments = env
                        .new_object_array(items, "java/lang/String", &JObject::null())
                        .unwrap();

                    for (i, c) in COMMENTBUFFERS
                        .lock()
                        .unwrap()
                        .get(&ind)
                        .unwrap()
                        .iter()
                        .enumerate()
                    {
                        env.set_object_array_element(&comments, i as i32, as_obj(c, &env))
                            .unwrap();
                    }
                    comments
                } else {
                    env.new_object_array(0, "java/lang/Object", &JObject::null())
                        .unwrap()
                }
            } else {
                env.new_object_array(0, "java/lang/Object", &JObject::null())
                    .unwrap()
            }
        }
    };

    // log!(env, "pre call??");

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
    // log!(env, "what??");
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_text<'local>(
    mut env: JNIEnv<'local>,
    _obj: JObject,
    text_str: JString,
    composer: JObject,
    changed: jint,
) {
    // log!(env, format!("test text"));
    // let s = env.new_string("lol").unwrap();
    let my_m = env.get_static_field(
        "androidx/compose/ui/Modifier",
        "Companion",
        "Landroidx/compose/ui/Modifier$Companion;",
    );
    let color = JValue::Long(0x7f20202000000000 - 0x7fffffffffffffff - 1);
    log!(env, format!("{color:?}"));
    let my_m = match my_m {
        Ok(m) => match m {
            JValueGen::Object(o) => o,
            _ => {
                return;
            }
        },
        Err(e) => {
            log!(env, format!("No mod: {e:?}"));
            return;
        }
    };
    let my_b = env.call_static_method("androidx/compose/foundation/BackgroundKt","background-bw27NRU$default" ,"(Landroidx/compose/ui/Modifier;JLandroidx/compose/ui/graphics/Shape;ILjava/lang/Object;)Landroidx/compose/ui/Modifier;" ,&[JValue::Object(&my_m), color, JValue::Object(&JObject::null()), JValue::Int(2), JValue::Object(&JObject::null())] ).unwrap_or_else(|e| {
        log!(env, format!("No bg{e:?}"));
        JValueGen::Void
    });
    let JValueGen::Object(my_b) = my_b else {
        return;
    };
    let my_s = env
        .call_static_method(
            "androidx/compose/foundation/layout/SizeKt",
            "fillMaxWidth$default",
            "(Landroidx/compose/ui/Modifier;FILjava/lang/Object;)Landroidx/compose/ui/Modifier;",
            &[
                JValue::Object(&my_b),
                JValue::Float(1.0),
                JValue::Int(2),
                JValue::Object(&JObject::null()),
            ],
        )
        .unwrap_or_else(|e| {
            log!(env, format!("No fill{e:?}"));
            JValueGen::Void
        });
    let JValueGen::Object(my_s) = my_s else {
        return;
    };
    let er = env.call_static_method("androidx/compose/material/TextKt", "Text--4IGK_g", "(Ljava/lang/String;Landroidx/compose/ui/Modifier;JJLandroidx/compose/ui/text/font/FontStyle;Landroidx/compose/ui/text/font/FontWeight;Landroidx/compose/ui/text/font/FontFamily;JLandroidx/compose/ui/text/style/TextDecoration;Landroidx/compose/ui/text/style/TextAlign;JIZIILkotlin/jvm/functions/Function1;Landroidx/compose/ui/text/TextStyle;Landroidx/compose/runtime/Composer;III)V",
        &[JValue::Object(&unsafe {JObject::from_raw(text_str.as_raw())}), JValue::Object(&unsafe {JObject::from_raw(my_s.as_raw())}), JValue::Long(0), JValue::Long(0), JValue::Object(&JObject::null()), JValue::Object(&JObject::null()), JValue::Object(&JObject::null()), JValue::Long(0), JValue::Object(&JObject::null()), JValue::Object(&JObject::null()), JValue::Long(0), JValue::Int(0), JValue::Bool(0), JValue::Int(0), JValue::Int(0), JValue::Object(&JObject::null()), JValue::Object(&JObject::null()), JValue::Object(&composer), JValue::Int(0), JValue::Int(0), JValue::Int(131068)]);
    // log!(env, format!("test: {er:?}"));
}
