import React, {useEffect} from 'react';
import { useDispatch, useSelector} from 'react-redux';
import { setUser } from "reducer/userReducer";
import http from "api/commonHttp";
import { useNavigate } from 'react-router-dom';
import Loading from 'components/user/login/Loading';

export default function Kakao() {
  const user = useSelector((state) => state.user);

  const dispatch = useDispatch();   
  const navigate = useNavigate();

  useEffect(() => {
      const code = new URL(window.location.href).searchParams.get("code");

      if(code) {
         http
        .get("user/auth/kakao?code=" + code)
        .then(({ data }) => {
          dispatch(setUser(data));
          if(data.user.isAlready===1) {
            navigate('/news');
          } else if(data.user.isAlready===0) {
            navigate( '/profile');
          }
        })
        .catch((err) => {
          console.error(err);
        }); 
      }
      
    }, []);

  return (
    <Loading></Loading>
  );
}

