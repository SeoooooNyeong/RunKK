"""탈퇴 회원 파기(S15P31A207-219, 개인정보처리방침 파기 조항).

api가 회원 탈퇴(MemberWithdrawn)·파기 지시 때 DELETE /users/{userId}를 부르면 analytics의 그 사용자 행을 지운다.
저장소 종류(runbody 결과·coach 대화)는 모르고 purge_user(user_id)를 가진 것이면 무엇이든 받는다(main이 끼운다).
"""

from runkk_ai.privacy.router import PrivacyRouter, get_withdrawal_service
from runkk_ai.privacy.service import PurgeError, UserDataPurger, WithdrawalService

__all__ = ["PrivacyRouter", "PurgeError", "UserDataPurger", "WithdrawalService", "get_withdrawal_service"]
