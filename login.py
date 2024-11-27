from flask import Flask, request, jsonify, session, flash
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models, Input
from tensorflow.keras.models import load_model
import os
from datetime import datetime
import pymysql
from flask_sqlalchemy import SQLAlchemy
from werkzeug.utils import secure_filename
from werkzeug.security import check_password_hash
import logging
import boto3
import requests
from PIL import Image
from io import BytesIO

# Flask 앱 초기화 및 데이터베이스 설정
app = Flask(__name__)
app.secret_key = 'your_secret_key'  # 보안을 위해 실제 프로젝트에서는 환경 변수로 관리하세요.
app.config['SQLALCHEMY_DATABASE_URI'] = 'mysql+pymysql://taehwan0215:1004zmdkqlalf!@plant-app-backend-database.cd4ykgcqcw1e.ap-northeast-2.rds.amazonaws.com/plant_app_backend'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)

# 로그 설정
logging.basicConfig(filename='app.log', level=logging.DEBUG, 
                    format='%(asctime)s - %(levelname)s - %(message)s')

# AWS S3 클라이언트 설정
aws_access_key_id = os.getenv('AKIAVA5YK4JWM7WL2HPW')
aws_secret_access_key = os.getenv('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')

s3_client = boto3.client(
    's3',
    region_name='ap-northeast-2',  # 적절한 AWS 리전으로 변경
    aws_access_key_id=aws_access_key_id,
    aws_secret_access_key=aws_secret_access_key
)

BUCKET_NAME = 'plant-app-image-storage'  # S3 버킷 이름
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}  # 허용할 이미지 확장자

# 파일 확장자 확인 함수
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

# User 모델 정의
class User(db.Model):
    __tablename__ = 'users'
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    password_hash = db.Column(db.String(120), nullable=False)
    nickname = db.Column(db.String(50), nullable=False)

# 게시글 모델 정의
class Post(db.Model):
    __tablename__ = 'post'
    Post_ID = db.Column(db.Integer, primary_key=True)
    User_ID = db.Column(db.Integer, db.ForeignKey('users.id'))
    Category = db.Column(db.String(80), nullable=False)
    Title = db.Column(db.String(200), nullable=False)
    Content = db.Column(db.Text, nullable=False)
    Created_date = db.Column(db.DateTime)
    Updated_date = db.Column(db.DateTime)
    Like_count = db.Column(db.Integer, default=0)
    Comment_count = db.Column(db.Integer, default=0)
    Image_url = db.Column(db.String(255))  # 이미지 URL 추가

# 모델 파일 경로 (서버에 업로드된 모델 파일)
MODEL_PATH = "/home/ec2-user/backend_code/watering_predictor_model_with_growth.h5"
MODEL_PATH_2 = "/home/ec2-user/backend_code/unified_model.h5"
# 모델 로드
if os.path.exists(MODEL_PATH):
    try:
        model = tf.keras.models.load_model(MODEL_PATH)  # 모델 구조와 가중치를 함께 로드
        app.logger.info("모델이 성공적으로 로드되었습니다.")
    except Exception as e:
        model = None
        app.logger.error(f"모델 로드 중 오류 발생: {e}")
else:
    model = None
    app.logger.error("오류: 모델 파일을 찾을 수 없습니다.")
# 두 번째 모델 로드
if os.path.exists(MODEL_PATH_2):
    try:
        model_2 = tf.keras.models.load_model(MODEL_PATH_2)
        app.logger.info("두 번째 모델이 성공적으로 로드되었습니다.")
    except Exception as e:
        model_2 = None
        app.logger.error(f"두 번째 모델 로드 중 오류 발생: {e}")
else:
    model_2 = None
    app.logger.error("오류: 두 번째 모델 파일을 찾을 수 없습니다.")
    

# 이미지 전처리 함수
def preprocess_image(image_array):
    # 이미지를 모델에 입력할 형태로 변화합니다. (예: (224, 224, 3) 크기)
    image_array = np.array(image_array).astype('float32') / 255.0
    image_array = np.expand_dims(image_array, axis=0)
    return image_array

# 식물 추가 라우트
@app.route('/add_plant', methods=['POST'])
def add_plant():
    try:
        user_id = request.form.get('user_id')
        nickname = request.form.get('nickname')
        image = request.files.get('image')

        if not user_id or not nickname or not image:
            return jsonify({'error': 'Invalid input'}), 400

        # 파일명 처리 및 S3 업로드
        if allowed_file(image.filename):
            try:
                filename = secure_filename(image.filename)
                file_path = os.path.join('/tmp', filename)
                image.save(file_path)
                s3_client.upload_file(file_path, BUCKET_NAME, filename)
                image_url = f"https://{BUCKET_NAME}.s3.amazonaws.com/{filename}"

                # 파일 삭제
                os.remove(file_path)
            except Exception as e:
                return jsonify({'error': f"이미지 업로드 실패: {str(e)}"}), 500
        else:
            return jsonify({'error': '지원하지 않는 파일 형식입니다'}), 400

        # 데이터베이스에 저장
        query = """
            INSERT INTO userplant (User_ID, Nickname, Date_added, watering, Growth)
            VALUES (%s, %s, %s, %s, %s)
        """
        db.session.execute(query, (user_id, nickname, datetime.now(), 0, 0))
        db.session.commit()

        return jsonify({'message': '식물이 성공적으로 추가되었습니다.', 'image_url': image_url}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500

# Pre-signed URL 생성 라우트
@app.route('/get-presigned-url', methods=['POST'])
def get_presigned_url():
    try:
        data = request.get_json()
        filename = data.get('filename')

        if not filename:
            return jsonify({'error': 'Filename is required'}), 401

        presigned_url = s3_client.generate_presigned_url(
            'put_object',
            Params={'Bucket': BUCKET_NAME, 'Key': filename},
            ExpiresIn=3600
        )

        return jsonify({'url': presigned_url}), 203
    except Exception as e:
        app.logger.error(f"Error generating pre-signed URL: {e}")
        return jsonify({'error': str(e)}), 509


# 이미지 전처리 함수
def preprocess_image(image_array):
    # 이미지를 PIL 이미지 객체로 변화하고 리사이즈합니다.
    image = Image.fromarray(image_array)
    image = image.resize((224, 224))

    # 이미지를 numpy 배열로 변화하고 모델에 입력할 형태로 변화합니다.
    image_array = np.array(image).astype('float32') / 255.0
    image_array = np.expand_dims(image_array, axis=0)
    return image_array

# 모델 분석 라우트 수정
@app.route('/analyze', methods=['POST'])
def analyze():
    try:
        # 프론트엔드에서 받은 데이터 (이미지 URL) 확인
        data = request.get_json()
        image_url = data.get('image_url')

        if image_url is None:
            app.logger.error("이미지 URL이 없습니다.")
            return jsonify({'error': '이미지 URL이 없습니다.'}), 402

        # 이미지 다운로드 및 전처리
        try:
            response = requests.get(image_url)
            if response.status_code != 200:
                raise Exception("S3에서 이미지 다운로드 실패")
            
            # 이미지를 PIL로 열고 리사이즈합니다.
            image = Image.open(BytesIO(response.content))
            image = image.resize((224, 224))

            # 이미지를 numpy 배열로 변화합니다.
            image_data = preprocess_image(np.array(image))
        except Exception as e:
            app.logger.error(f"S3에서 이미지 다운로드 중 오류 발생: {e}")
            return jsonify({'error': f"이미지 다운로드 오류: {str(e)}"}), 501

        # 모델 예측 중 예외 조치
        try:
            interval_pred, amount_pred, height_pred, thickness_pred = model.predict(image_data)

            # 예측 결과를 0 이상의 값으로 보정
            watering_interval = max(0, float(interval_pred[0]))
            growth_height = max(0, float(height_pred[0]))
        except Exception as e:
            app.logger.error(f"모델 예측 중 오류 발생: {e}")
            return jsonify({'error': f"예측 오류: {str(e)}"}), 503
        # 결과 반환
        response = {
            'watering_interval': watering_interval,
            'growth_height': growth_height
        }
        app.logger.info(f"응답 반환: {response}")
        return jsonify(response)

    except Exception as e:
        app.logger.error(f"analyze 엔드포인트에서 예기치 않은 오류 발생: {e}")
        return jsonify({'error': str(e)}), 504

# 클래스 이름 정의 (식물 클래스)
class_indices = {
    0: "금전수",
    1: "몬스테라",
    2: "보스턴고사리",
    3: "스투키",
    4: "스파티필럼",
    5: "호접란"
}

# 식물 식별 라우트 추가
@app.route('/identify_plant', methods=['POST'])
def identify_plant():
    try:
        # 프론트엔드에서 받은 데이터 (이미지 URL) 확인
        data = request.get_json()
        image_url = data.get('image_url')

        if image_url is None:
            app.logger.error("이미지 URL이 없습니다.")
            return jsonify({'error': '이미지 URL이 없습니다.'}), 405

        # 이미지 다운로드 및 전처리
        try:
            response = requests.get(image_url)
            if response.status_code != 200:
                raise Exception("S3에서 이미지 다운로드 실패")
            
            # 이미지를 PIL로 열고 리사이즈합니다.
            image = Image.open(BytesIO(response.content))
            image = image.resize((224, 224))

            # 이미지를 numpy 배열로 변화합니다.
            image_data = preprocess_image(np.array(image))
        except Exception as e:
            app.logger.error(f"S3에서 이미지 다운로드 중 오류 발생: {e}")
            return jsonify({'error': f"이미지 다운로드 오류: {str(e)}"}), 505

        # 두 번째 모델 예측 중 예외 조치 (식물 식별)
        try:
            predictions = model_2.predict(image_data)
            # 가장 높은 확률을 가지는 클래스 하나만 반환하도록 수정
            top_index = np.argmax(predictions[0])
            identified_class = class_indices[top_index]
            confidence = predictions[0][top_index]

            response = {
                'identified_class': identified_class,
                'confidence': float(confidence)
            }
        except Exception as e:
            app.logger.error(f"식물 식별 모델 예측 중 오류 발생: {e}")
            return jsonify({'error': f"예측 오류: {str(e)}"}), 506

        app.logger.info(f"응답 반환: {response}")
        return jsonify(response)

    except Exception as e:
        app.logger.error(f"identify_plant 엔드포인트에서 예기치 않은 오류 발생: {e}")
        return jsonify({'error': str(e)}), 507

# 로그인 라우트
@app.route('/login', methods=['POST'])
def login():
    username = request.json.get('username')
    password = request.json.get('password')
    # 데이터베이스에서 사용자 조회
    user = User.query.filter_by(username=username).first()

    if user and check_password_hash(user.password_hash, password):
        session['logged_in'] = True
        session['user_id'] = user.id
        flash('로그인 성공')
        return jsonify({"message": "로그인 성공", "status": "success"}), 200
    else:
        flash('로그인 실패')
        return jsonify({"message": "로그인 실패", "status": "failure"}), 401

# 로그아웃 라우트
@app.route('/logout')
def logout():
    session.pop('logged_in', None)
    session.pop('user_id', None)
    flash('로그아웃 성공')
    return jsonify({"message": "로그아웃 성공", "status": "success"}), 200

# 게시글 작성
@app.route('/posts', methods=['POST'])
def create_post():
    data = request.form
    title = data['title']
    content = data['content']
    category = data['category']
    
    try:
        # user_id를 문자열로 받았기 때문에 int()로 변환
        user_id = int(data['user_id'])  # 작성자의 User_ID를 정수로 변환
        print(f"Received user_id: {user_id}")  # user_id 로그
    except ValueError:
        print(f"Invalid user_id received: {data['user_id']}")  # user_id 변환 오류 로그
        return jsonify({"message": "user_id가 잘못되었습니다. 정수여야 합니다."}), 400

    image_url = None

    # 파일이 포함된 경우 처리
    if 'file' in request.files:
        file = request.files['file']
        if file and allowed_file(file.filename):
            try:
                # 파일명 처리
                filename = secure_filename(file.filename)
                file_path = os.path.join('/tmp', filename)  # 임시 디렉토리
                file.save(file_path)

                # S3에 업로드
                s3_client.upload_file(file_path, BUCKET_NAME, filename)
                image_url = f"https://{BUCKET_NAME}.s3.amazonaws.com/{filename}"  # S3 URL 생성
                print(f"File uploaded to S3 with URL: {image_url}")  # S3 업로드 로그

                # 파일 삭제 (서버에 임시 저장된 파일을 삭제)
                os.remove(file_path)
            except Exception as e:
                print(f"Error uploading file to S3: {str(e)}")  # S3 업로드 오류 로그
                return jsonify({"message": "S3에 파일 업로드 중 오류 발생", "error": str(e)}), 500

    try:
        new_post = Post(
            User_ID=user_id, Title=title, Content=content, Category=category,
            Created_date=db.func.now(), Image_url=image_url  # Image_url을 함께 저장
        )
        db.session.add(new_post)
        db.session.commit()
        return jsonify({"message": "게시글이 성공적으로 생성되었습니다."}), 201
    except Exception as e:
        print(f"게시글 생성 중 오류 발생: {str(e)}")  # 게시글 생성 오류 로그
        return jsonify({"message": f"오류: {str(e)}"}), 500

# 게시글 리스트 가져오기 (페이징 적용)
@app.route('/posts', methods=['GET'])
def get_posts():
    try:
        page = int(request.args.get('page', 1))  # 현재 페이지
        page_size = int(request.args.get('page_size', 5))  # 한 번에 가져올 게시글 개수
        offset = (page - 1) * page_size  # 시작 위치 계산

        # 게시글 리스트 코드 (페이징 적용, 닉네임 포함)
        posts_query = db.session.query(Post, User.nickname).join(User).order_by(Post.Created_date.desc()).limit(page_size).offset(offset)
        posts = [{"Post_ID": post.Post_ID, "User_Nickname": nickname, "Title": post.Title, "Category": post.Category,
                  "Content": post.Content, "Created_date": post.Created_date, "Like_count": post.Like_count,
                  "Comment_count": post.Comment_count, "Image_url": post.Image_url}  # Image_url 포함
                 for post, nickname in posts_query]

        # 총 게시글 수 가져오기
        total_posts = db.session.query(db.func.count(Post.Post_ID)).scalar()

        # 응답 데이터에 총 게시글 수 포함

        return jsonify({
            "posts": posts,
            "total": total_posts,
            "page": page,
            "page_size": page_size
        })
    except Exception as e:
        return jsonify({"message": f"오류: {str(e)}"}), 500

# 게시글 상세 보기
@app.route('/posts/<int:post_id>', methods=['GET'])
def get_post(post_id):
    try:
        post = Post.query.filter_by(Post_ID=post_id).first()
        if post:
            return jsonify({
                "Post_ID": post.Post_ID,
                "User_ID": post.User_ID,
                "Category": post.Category,
                "Title": post.Title,
                "Content": post.Content,
                "Created_date": post.Created_date,
                "Updated_date": post.Updated_date,
                "Like_count": post.Like_count,
                "Comment_count": post.Comment_count,
                "Image_url": post.Image_url  # Image_url 추가
            })
        else:
            return jsonify({"message": "게시글을 찾을 수 없습니다."}), 404
    except Exception as e:
        return jsonify({"message": f"오류: {str(e)}"}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=True)
