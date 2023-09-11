import React from "react";
import { Link } from "react-router-dom";
import styled from "styled-components";

export default function Account() {
  return (
    <div style={{ gap: "240px" }}>
      <hr style={{ border: "0.5px solid #EDEDED", margin: "24px 0px" }} />
      <StyledLink to="/mypage">
        <img src="/icons/header/ic_dictionary.svg" alt="dictionary" />
        nickname
      </StyledLink>
    </div>
  );
}

const StyledLink = styled(Link)`
  display: flex;
  gap: 14px;
  align-items: center;
  text-decoration: none;
  color: #35383b;
  font-size: 1rem;
`;