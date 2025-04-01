## 서비스별 개요

게시판 생성/수정/삭제/조회 기능을 담당하는 서비스입니다.

사용자 서비스와 연동되어 게시판 삭제 권한을 확인하며 게시글을 관리합니다.

게시글 작성 시 AWS S3를 활용하여 뉴스 기사를 PDF 파일로 저장하고 관리합니다.
<br><br>

##  빌드 및 실행 방법 (Jenkins + ECS + ECR 기반)
Jenkins를 활용한 자동화 빌드 및 배포 파이프라인과, AWS ECS 기반의 컨테이너 실행 및 관리 체계를 구성하였습니다. 빌드된 Docker 이미지는 latest 태그로 ECR에 푸시되며, ECS 서비스는 해당 태그를 기준으로 항상 최신 버전을 배포합니다. 일반적인 Jenkinsfile 파이프라인 예시는 다음과 같습니다.

### 📦 Jenkinsfile 예시

```bash
Pipeline {
  Tools: gradle 8.12.1

  Stages:
    1. Gradle Build
       - GitHub에서 코드 pull
       - gradle clean build -x test

    2. Docker Build & Deploy (조건: DOCKER_BUILD == true)
       - SSH로 Docker 서버 접속
       - Docker 로그인 (ECR)
       - 이미지 빌드 및 태깅
       - Docker 이미지 push (ECR)
       - ECS 태스크 정의 json 추출 → jq로 필터링
       - ECS 태스크 재등록
       - ECS 서비스에 새 태스크 배포
}
```
<br>

###  환경 변수 설정

ECS 태스크 정의 시 environment로 설정
- board-service의 ECS Task 정의 내 환경변수 예시

```json
"environment": [
    {
        "name": "AWS_REGION",
        "value": "example-region"
    },
    {
        "name": "MYSQL_PASSWORD",
        "value": "example-password"
    },
    {
        "name": "AWS_SECRETKEY",
        "value": "example-secret-key"
    },
    {
        "name": "DATABASE_NAME",
        "value": "example-database"
    },
    {
        "name": "DATABASE_ENDPOINT",
        "value": "example-db-endpoint"
    },
    {
        "name": "MYSQL_USERNAME",
        "value": "example-username"
    },
    {
        "name": "AWS_S3_BUCKET_NAME",
        "value": "example-s3-bucket"
    },
    {
        "name": "AWS_ACCESSKEY",
        "value": "example-access-key"
    },
    {
        "name": "BOARD_HOSTNAME",
        "value": "example-board-hostname"
    }
]
```
